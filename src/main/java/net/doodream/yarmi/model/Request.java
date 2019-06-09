package net.doodream.yarmi.model;


import net.doodream.yarmi.net.ClientSocketAdapter;
import net.doodream.yarmi.net.ServiceAdapter;
import net.doodream.yarmi.net.ServiceProxy;
import net.doodream.yarmi.net.session.BlobSession;
import net.doodream.yarmi.net.session.SessionControlMessage;
import net.doodream.yarmi.net.session.SessionControlMessageWriter;
import net.doodream.yarmi.parameter.Param;
import net.doodream.yarmi.serde.Writer;
import io.reactivex.Observable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Request contains information for client method invocation consisted with below
 * 1. endpoint which uniquely mapped to a method with 1:1 relation.
 * 2. parameters for method invocation
 * 3. optionally it conveys session control message which consumed by {@link ServiceAdapter} or {@link ServiceProxy}
 */
public class Request {


    private Request() { }


    public static class Builder {
        private final Request request = new Request();

        public Builder scm(SessionControlMessage controlMessage) {
            request.scm = controlMessage;
            return this;
        }

        public Request build() {
            return request;
        }

        public Builder params(List params) {
            request.params = params;
            return this;
        }

        public Builder endpoint(String unique) {
            request.endpoint = unique;
            return this;
        }

        public Builder session(BlobSession session) {
            request.session = session;
            return this;
        }
    }

    private static final Logger Log = LoggerFactory.getLogger(Request.class);

    private transient ClientSocketAdapter client;

    private transient Response response;

    private BlobSession session;

    private String endpoint;

    private List<Param> params;

    private SessionControlMessage scm;

    private int nonce;

    // TODO : blob header & blob

    public static boolean isValid(Request request) {
        return (request.getEndpoint() != null) &&
                (request.getParams() != null);
    }

    public static SessionControlMessageWriter buildSessionMessageWriter(final Writer writer) {
        return new SessionControlMessageWriter() {
            @Override
            public void write(SessionControlMessage controlMessage) throws IOException {
                writer.write(Request.builder()
                        .scm(controlMessage)
                        .build());
            }
        };
    }

    private static Builder builder() {
        return new Builder();
    }

    public synchronized void setResponse(Response response) {
        Log.trace("notify waiting thread for response ({}) : {}", nonce, response.getBody());
        this.response = response;
        this.notifyAll();
    }


    public void setClient(ClientSocketAdapter adapter) {
        this.client = adapter;
    }

    public synchronized Response getResponse(long timeout) throws TimeoutException {
        if(response != null) {
            return response;
        }
        Log.trace("wait for response ({}) with timeout {}", nonce, timeout);
        try {
            if (timeout > 0) {
                this.wait(timeout);
            } else {
                this.wait();
            }
            if (response == null) {
                Log.error("unexpected null result @ ({})", nonce);
                return RMIError.TIMEOUT.getResponse();
            }
            Log.trace("wake from waiting response ({}) @ {}", nonce, response.getBody());
            return response;
        } catch (InterruptedException e) {
            throw new TimeoutException(e.getMessage());
        }
    }

    public boolean hasScm() {
        return scm != null;
    }

    public int getNonce() {
        return nonce;
    }

    public static Request fromEndpoint(Endpoint endpoint, Object ...args) {
        if(args == null) {
            return Request.builder()
                    .params(Collections.EMPTY_LIST)
                    .endpoint(endpoint.getUnique())
                    .build();
        } else {
            BlobSession session = BlobSession.findOne(args);
            final Request.Builder builder =  Request.builder()
                    .params(convertParams(endpoint, args))
                    .endpoint(endpoint.getUnique());

            if(!session.equals(BlobSession.NULL)) {
                builder.session(session);
            }

            return builder.build();
        }
    }

    private static List<Param> convertParams(final Endpoint endpoint, Object[] objects) {
        if(objects == null) {
            return Collections.EMPTY_LIST;
        }

        return Observable.fromIterable(endpoint.getParams()).zipWith(Observable.fromArray(objects), new BiFunction<Param, Object, Param>() {
            @Override
            public Param apply(Param param, Object o) throws Exception {
                param.apply(o);
                if(param.isInstanceOf(BlobSession.class)) {
                    if(o != null) {
                        endpoint.session = (BlobSession) o;
                    }
                }
                return param;
            }
        }).collectInto(new ArrayList<Param>(), new BiConsumer<ArrayList<Param>, Param>() {
            @Override
            public void accept(ArrayList<Param> params, Param param) throws Exception {
                params.add(param);
            }
        }).blockingGet();
    }

    public void setNonce(int nonce) {
        this.nonce = nonce;
    }

    public BlobSession getSession() {
        return session;
    }

    public List<Param> getParams() {
        return params;
    }

    public Response getResponse() {
        return response;
    }

    public SessionControlMessage getScm() {
        return scm;
    }

    public String getEndpoint() {
        return endpoint;
    }
}