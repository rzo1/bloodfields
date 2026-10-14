package com.github.rzo1.bloodfields.net;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

import javax.net.ssl.SSLException;
import java.io.File;

/**
 * TLS contexts for the match wire, built on the JDK provider (the shaded
 * Netty's tcnative natives are excluded from the fat jar by design — the
 * JDK provider needs none). Opt-in everywhere: plaintext remains the
 * default for loopback/trusted-LAN play, and the servers say so loudly.
 */
final class Tls {

    private Tls() {}

    /** Server context from a PEM certificate chain + private key. */
    static SslContext serverContext(File certChain, File privateKey) throws SSLException {
        SslContextBuilder builder = GrpcSslContexts.forServer(certChain, privateKey);
        return GrpcSslContexts.configure(builder, SslProvider.JDK).build();
    }

    /**
     * Client context. With {@code trustCert} non-null, only that PEM
     * (typically the server's self-signed certificate) is trusted; with
     * null, the platform roots apply.
     */
    static SslContext clientContext(File trustCert) throws SSLException {
        SslContextBuilder builder = GrpcSslContexts.forClient();
        if (trustCert != null) {
            builder.trustManager(trustCert);
        }
        return GrpcSslContexts.configure(builder, SslProvider.JDK).build();
    }
}
