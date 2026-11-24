package org.kivpn.model;

public record Profile(Long id, Long sourceId, String name, String protocol, String address, int port,
                      String uri, String params, boolean active) {
}
