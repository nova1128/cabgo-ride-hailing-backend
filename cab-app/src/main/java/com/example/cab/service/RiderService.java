package com.example.cab.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cab.dto.RegisterRiderRequest;
import com.example.cab.dto.RiderResponse;
import com.example.cab.exception.ConflictException;
import com.example.cab.exception.NotFoundException;
import com.example.cab.model.Rider;
import com.example.cab.model.Trip;
import com.example.cab.repository.RiderRepository;

/** Rider module service. Other modules call requireExists(...) instead of touching the repository. */
@Service
public class RiderService {

    private final RiderRepository repository;

    public RiderService(RiderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RiderResponse register(RegisterRiderRequest request) {
        if (repository.existsByPhone(request.phone())) {
            throw new ConflictException("A rider with this phone already exists");
        }
        Rider saved = repository.save(new Rider(request.name(), request.phone()));
        return RiderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RiderResponse get(Long id) {
        Rider rider = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rider " + id + " not found"));
        return RiderResponse.from(rider);
    }

    // Other modules (like Trip) call this instead of touching RiderRepository directly.
    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Rider " + id + " not found");
        }
    }
}
