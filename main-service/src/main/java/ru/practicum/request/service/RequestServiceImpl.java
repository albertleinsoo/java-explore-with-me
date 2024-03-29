package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.event.dto.EventRequestStatusUpdateRequest;
import ru.practicum.event.dto.EventRequestStatusUpdateResult;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.enums.EventState;
import ru.practicum.event.model.enums.EventStatus;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ObjectNotFoundException;
import ru.practicum.exception.RequestConflictException;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.mapper.RequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.model.ParticipationStatus;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RequestMapper requestMapper;

    @Override
    public ParticipationRequestDto addParticipationRequest(Long userId, Long eventId) {
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new RequestConflictException("Participation request with userId = " + userId
                    + " eventId = " + eventId + " already exists.");
        }

        User requester = userRepository.findById(userId).orElseThrow(() -> {
            throw new ObjectNotFoundException("User with id = " + userId + " was not found.");
        });

        Event event = eventRepository.findById(eventId).orElseThrow(() -> {
            throw new ObjectNotFoundException("Event with id = " + eventId + " doesn't exist.");
                });

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new RequestConflictException("Users are not allowed to register for unpublished events.");
        }

        if (Objects.equals(userId, event.getInitiator().getId())) {
            throw new RequestConflictException("Event organizers are not allowed to request participation in their own events.");
        }

        if ((event.getParticipantLimit() != 0L)
                && (requestRepository.countByEventIdAndStatus(eventId, ParticipationStatus.CONFIRMED) >= event.getParticipantLimit())) {
            throw new RequestConflictException("Participant limit reached.");
        }

        ParticipationRequest requestToSave = new ParticipationRequest(requester, event,
                !event.getRequestModeration() || event.getParticipantLimit() == 0L ?
                        ParticipationStatus.CONFIRMED : ParticipationStatus.PENDING, LocalDateTime.now());

        ParticipationRequest participationRequest = requestRepository.save(requestToSave);

        return requestMapper.requestToDto(participationRequest);
    }

    @Override
    public ParticipationRequestDto cancelParticipationRequest(Long userId, Long requestId) {
        ParticipationRequest request = requestRepository.findByIdAndRequesterId(requestId, userId).orElseThrow(() -> {
            throw new ObjectNotFoundException("Participation request with id = " + requestId + " doesn't exist.");
        });

        if (request.getStatus() == ParticipationStatus.CONFIRMED) {
            throw new RequestConflictException("Participation request with id = " + requestId + " is already confirmed.");
        }

        request.setStatus(ParticipationStatus.CANCELED);

        Long eventId = request.getEvent().getId();
        Event event = eventRepository.findById(eventId).orElseThrow(() -> {
            throw new ObjectNotFoundException("Event with id = " + eventId + " doesn't exist.");
        });

        eventRepository.save(event);

        request = requestRepository.save(request);
        return requestMapper.requestToDto(request);
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        List<ParticipationRequest> requests = requestRepository.findAllByRequesterId(userId);

        return requests.stream()
                .map(requestMapper::requestToDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ParticipationRequestDto> getUserEventRequests(Long userId, Long eventId) {
        List<ParticipationRequest> requests = requestRepository.findAllByEventIdAndEventInitiatorId(eventId, userId);

        return requests.stream()
                    .map(requestMapper::requestToDto)
                    .collect(Collectors.toList());
    }

    @Override
    public EventRequestStatusUpdateResult updateEventRequests(Long userId, Long eventId,
                                                              @Valid EventRequestStatusUpdateRequest requestsUpdate) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId).orElseThrow(() -> {
            throw new ObjectNotFoundException("Event with id = " + eventId + " and user id = " + userId + " doesn't exist.");
        });

        if (!event.getInitiator().getId().equals(userId)) {
            throw new RequestConflictException("Access denied. User with id = " + userId + " is not an event initiator.");
        }

        List<ParticipationRequest> participationRequests = requestRepository.findAllByIdInAndAndEventId(requestsUpdate.getRequestIds(), eventId);

        if (participationRequests.size() != requestsUpdate.getRequestIds().size()) {
            throw new ObjectNotFoundException("Incorrect request id(s) received in the request body.");
        }

        for (ParticipationRequest request : participationRequests) {
            if (!request.getStatus().equals(ParticipationStatus.PENDING)) {
                throw new RequestConflictException("Only requests with status 'Pending' can be accepted or rejected.");
            }
        }

        List<ParticipationRequest> confirmedRequests = new ArrayList<>();
        List<ParticipationRequest> rejectedRequests = new ArrayList<>();

        if (requestsUpdate.getStatus() == EventStatus.REJECTED) {
            participationRequests.forEach(participationRequest -> {
                participationRequest.setStatus(ParticipationStatus.REJECTED);
                rejectedRequests.add(participationRequest);
            });
            requestRepository.saveAll(rejectedRequests);
            return new EventRequestStatusUpdateResult(
                    confirmedRequests.stream()
                            .map(requestMapper::requestToDto).collect(Collectors.toList()),
                    rejectedRequests.stream()
                            .map(requestMapper::requestToDto).collect(Collectors.toList()));
        }

        if (event.getParticipantLimit() == 0 || !event.getRequestModeration()) {
            return new EventRequestStatusUpdateResult(
                    participationRequests.stream()
                            .peek(rq -> rq.setStatus(ParticipationStatus.CONFIRMED))
                            .map(requestMapper::requestToDto).collect(Collectors.toList()),
                    new ArrayList<>()
            );
        }

        long confirmedRequestsCount = requestRepository.countByEventIdAndEventInitiatorIdAndStatus(eventId, userId, ParticipationStatus.CONFIRMED);

        if (confirmedRequestsCount >= event.getParticipantLimit()) {
            throw new RequestConflictException("Failed to accept request. Reached max participant limit for event id = " + eventId + ".");
        }

        for (ParticipationRequest participationRequest: participationRequests) {
            if (confirmedRequestsCount < event.getParticipantLimit()) {
                participationRequest.setStatus(ParticipationStatus.CONFIRMED);
                confirmedRequests.add(participationRequest);
                confirmedRequestsCount += 1;
            } else {
                participationRequest.setStatus(ParticipationStatus.REJECTED);
                rejectedRequests.add(participationRequest);
            }
        }

        requestRepository.saveAll(Stream.concat(confirmedRequests.stream(),rejectedRequests.stream()).collect(Collectors.toList()));

        if (!confirmedRequests.isEmpty()) {
            eventRepository.save(event);
        }

        return new EventRequestStatusUpdateResult(
                confirmedRequests.stream()
                        .map(requestMapper::requestToDto).collect(Collectors.toList()),
                rejectedRequests.stream()
                        .map(requestMapper::requestToDto).collect(Collectors.toList()));
    }
}
