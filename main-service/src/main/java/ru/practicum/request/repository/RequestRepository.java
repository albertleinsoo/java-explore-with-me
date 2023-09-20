package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.model.ParticipationStatus;

import java.util.List;
import java.util.Optional;

public interface RequestRepository extends JpaRepository<ParticipationRequest, Long> {

    Boolean existsByRequesterIdAndEventId(Long userId, Long eventId);

    List<ParticipationRequest> findAllByIdInAndAndEventId(Iterable<Long> ids, Long eventId);

    List<ParticipationRequest> findAllByRequesterId(Long userId);

    List<ParticipationRequest> findAllByEventIdAndEventInitiatorId(Long eventId, Long userId);

    Long countByEventIdAndEventInitiatorIdAndStatus(Long eventId, Long userId, ParticipationStatus status);

    Long countByEventIdAndStatus(Long eventId, ParticipationStatus status);

    List<ParticipationRequest> findAllByStatus(ParticipationStatus status);

    Optional<ParticipationRequest> findByIdAndRequesterId(Long requestId, Long userId);

}
