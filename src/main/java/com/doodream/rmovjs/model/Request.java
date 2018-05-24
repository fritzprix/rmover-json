package com.doodream.rmovjs.model;


import com.doodream.rmovjs.method.RMIMethod;
import com.doodream.rmovjs.net.ClientSocketAdapter;
import com.doodream.rmovjs.net.RMISocket;
import com.doodream.rmovjs.util.SerdeUtil;
import com.doodream.rmovjs.parameter.Param;
import com.google.common.base.Preconditions;
import com.google.gson.annotations.SerializedName;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Request {

    private transient ClientSocketAdapter client;
    @SerializedName("endpoint")
    private Endpoint endpoint;



    public void response(Response s) throws IOException {
        if(client == null) {
            throw new IOException("No Client");
        }
        client.write(s);
    }

    public final RMIMethod getMethodType() {
        return endpoint.method;
    }

    public final String getPath() {
        return endpoint.path;
    }

    public final List<Param> getParameters() {
        return endpoint.params;
    }

    public static Request fromJson(String json) {
        return SerdeUtil.fromJson(json, Request.class);
    }

    public static String toJson(Request request) {
        return SerdeUtil.toJson(request);
    }

    public static boolean isValid(Request request) {
        return (request.getEndpoint() != null) &&
                (request.getPath() != null) &&
                (request.getParameters() != null) &&
                (request.getMethodType() != null);
    }

    public Response to(RMISocket socket) throws IOException {
        socket.getOutputStream().write(SerdeUtil.toByteArray(this));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        return Response.fromJson(reader.readLine());
    }

    public static Observable<Request> from(RMISocket client) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        Observable<String> lineObservable = Observable.create(emitter -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    emitter.onNext(line);
                }
                emitter.onComplete();
            } catch (IOException e) {
                emitter.onError(e);
            }
        });
        return lineObservable.subscribeOn(Schedulers.io()).map(Request::fromJson);
    }
}
