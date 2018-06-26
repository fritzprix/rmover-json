package com.doodream.rmovjs.model;


import com.doodream.rmovjs.net.ClientSocketAdapter;
import com.doodream.rmovjs.net.session.BlobSession;
import com.doodream.rmovjs.net.session.SessionControlMessage;
import com.doodream.rmovjs.net.session.SessionControlMessageWriter;
import com.doodream.rmovjs.parameter.Param;
import com.doodream.rmovjs.serde.Converter;
import com.doodream.rmovjs.serde.RMIWriter;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Request contains information for client method invocation consisted with below
 * 1. endpoint which uniquely mapped to a method with 1:1 relation.
 * 2. parameters for method invocation
 * 3. optionally it conveys session control message which consumed by {@link com.doodream.rmovjs.net.ServiceAdapter} or {@link com.doodream.rmovjs.net.RMIServiceProxy}
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Request {

    private transient ClientSocketAdapter client;
    private transient Response response;

    @SerializedName("session")
    private BlobSession session;

    @SerializedName("endpoint")
    private String endpoint;

    @SerializedName("params")
    private List<Param> params;

    @SerializedName("scm")
    private SessionControlMessage scm;

    @SerializedName("nonce")
    private int nonce;

    // TODO : blob header & blob

    public static boolean isValid(Request request) {
        return (request.getEndpoint() != null) &&
                (request.getParams() != null);
    }

    public static SessionControlMessageWriter buildSessionMessageWriter(RMIWriter writer) {
        // TODO : return SessionControlMessageWriter
        return new SessionControlMessageWriter() {

            @Override
            public void write(SessionControlMessage controlMessage) throws IOException {
                writer.write(Response.builder().scm(controlMessage).build());
            }

            @Override
            public void writeWithBlob(SessionControlMessage controlMessage, InputStream data) throws IOException {
                writer.writeWithBlob(Response.builder().scm(controlMessage).build(), data);
            }

            @Override
            public void writeWithBlob(SessionControlMessage controlMessage, ByteBuffer buffer) throws IOException {
                writer.writeWithBlob(Response.builder().scm(controlMessage).build(), buffer);
            }
        };
    }

    public boolean hasScm() {
        return scm != null;
    }
}
