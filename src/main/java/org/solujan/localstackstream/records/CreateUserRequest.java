package org.solujan.localstackstream.records;

public record CreateUserRequest(
        String id,
        String name,
        String email
) {
}
