package com.example.cab.dto;

import com.example.cab.model.Rider;

public record RiderResponse(Long id, String name, String phone) {

    public static RiderResponse from(Rider r) {
        return new RiderResponse(r.getId(), r.getName(), r.getPhone());
    }
}
