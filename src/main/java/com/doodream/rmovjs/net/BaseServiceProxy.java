package com.doodream.rmovjs.net;

import com.doodream.rmovjs.model.*;
import com.doodream.rmovjs.net.session.BlobSession;
import com.doodream.rmovjs.net.session.SessionControlMessage;
import com.doodream.rmovjs.serde.Converter;
import com.doodream.rmovjs.serde.Reader;
import com.doodream.rmovjs.serde.Writer;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class BaseServiceProxy implements RMIServiceProxy {

    private static final Logger Log = LoggerFactory.getLogger(BaseServiceProxy.class);

    private volatile boolean isOpened;
    private ConcurrentHashMap<String, BlobSession> sessionRegistry;
    private ConcurrentHashMap<Integer, Request> requestWaitQueue;
    private CompositeDisposable compositeDisposable;
    private int requestNonce;
    private RMIServiceInfo serviceInfo;
    private RMISocket socket;
    private Converter converter;
    private Reader reader;
    private Writer writer;


    public static BaseServiceProxy create(RMIServiceInfo info, RMISocket socket)  {
        return new BaseServiceProxy(info, socket);
    }

    private BaseServiceProxy(RMIServiceInfo info, RMISocket socket)  {
        sessionRegistry = new ConcurrentHashMap<>();
        requestWaitQueue = new ConcurrentHashMap<>();
        compositeDisposable = new CompositeDisposable();
        requestNonce = 0;
        serviceInfo = info;
        isOpened = false;
        this.socket = socket;
    }

    @Override
    public synchronized void open() throws IOException, IllegalAccessException, InstantiationException {
        if(isOpened) {
            return;
        }
        RMINegotiator negotiator = (RMINegotiator) serviceInfo.getNegotiator().newInstance();
        this.converter = (Converter) serviceInfo.getConverter().newInstance();
        socket.open();
        reader = converter.reader(socket.getInputStream());
        writer = converter.writer(socket.getOutputStream());
        socket = negotiator.handshake(socket, serviceInfo, converter, true);
        Log.info("open proxy for {} : success", serviceInfo.getName());
        isOpened = true;

        compositeDisposable.add(Observable.<Response>create(emitter -> {
            while (isOpened) {
                Response response = reader.read(Response.class);
                if(response == null) {
                    return;
                }
                if(response.hasScm()) {
                    handleSessionControlMessage(response);
                    continue;
                }
                emitter.onNext(response);
            }
            emitter.onComplete();
        }).subscribeOn(Schedulers.io()).subscribe(response -> {
            Request request = requestWaitQueue.remove(response.getNonce());
            if(request == null) {
                Log.warn("no mapped request exists : {}", response);
                return;
            }
            synchronized (request) {
                request.setResponse(response);
                request.notifyAll(); // wakeup waiting thread
            }
        }));
    }

    @Override
    public boolean isOpen() {
        return isOpened;
    }

    @Override
    public Response request(Endpoint endpoint, Object ...args) {

        return Observable.just(endpoint.toRequest(args))
                .doOnNext(request -> request.setNonce(++requestNonce))
                .doOnNext(this::registerSession)
                .doOnNext(request -> Log.debug("request => {}", request))
                .map(request -> {
                    requestWaitQueue.put(request.getNonce(), request);
                    // TODO : concurrent test required
                    writer.write(request);
                    synchronized (request) {
                        // caller block here
                        request.wait();
                    }
                    return Optional.ofNullable(request.getResponse());
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .defaultIfEmpty(RMIError.UNHANDLED.getResponse())
                .doOnError(this::onError)
                .subscribeOn(Schedulers.io())
                .blockingSingle();
    }

    private void handleSessionControlMessage(Response response) throws IOException {
        SessionControlMessage scm = response.getScm();
        BlobSession session = sessionRegistry.get(scm.getKey());
        if(session == null) {
            return;
        }
        session.handle(scm);
    }

    private void registerSession(Request request) {
        BlobSession session = request.getSession();
        if(session == null) {
            return;
        }
        if(sessionRegistry.put(session.getKey(), session) != null) {
            Log.warn("session : {} collision in registry", session.getKey());
        }
        session.start(reader, writer, Request::buildSessionMessageWriter, () -> unregisterSession(session));
    }

    private void unregisterSession(BlobSession session ) {
        if(sessionRegistry.remove(session.getKey()) == null) {
            Log.warn("fail to remove session : session not exists {}", session.getKey());
        } else {
            Log.debug("remove session : {}", session.getKey());
        }
    }

    private void onError(Throwable throwable) {
        Log.error("{}", throwable);
        try {
            close();
        } catch (IOException ignored) { }
    }

    public void close() throws IOException {
        if(!isOpened) {
            return;
        }
        if(socket.isClosed()) {
            return;
        }
        socket.close();
        isOpened = false;
        if(!compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
        }
        compositeDisposable.clear();
        // wake blocked thread from wait queue
        requestWaitQueue.values().forEach(request -> {
            synchronized (request) {
                request.notifyAll();
            }
        });
        Log.debug("proxy for {} closed", serviceInfo.getName());
    }

    @Override
    public long ping() {
        return 0;
    }

    @Override
    public boolean provide(Class controller) {
        return Observable.fromIterable(serviceInfo.getControllerInfos())
                .map(ControllerInfo::getStubCls)
                .map(controller::equals)
                .blockingFirst(false);
    }
}