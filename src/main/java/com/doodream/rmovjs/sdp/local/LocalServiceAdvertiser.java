package com.doodream.rmovjs.sdp.local;

import com.doodream.rmovjs.model.RMIServiceInfo;
import com.doodream.rmovjs.sdp.ServiceAdvertiser;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.Data;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 *  this class is to be used in testing as a simple placeholder for service discovery
 *  feature within local machine.
 *
 *  advertising will be started by writing service registry into a key-value cache in local machine
 *
 */
@Data
public class LocalServiceAdvertiser implements ServiceAdvertiser {

    public static final int BROADCAST_PORT = 3041;
    private Disposable disposable;


    @Override
    public synchronized void startAdvertiser(RMIServiceInfo info) {
        disposable = Observable.interval(0L, 10L, TimeUnit.SECONDS)
                .map(aLong -> info)
                .map(this::buildBroadcastPackaet)
                .doOnNext(this::broadcast)
                .doOnError(Throwable::printStackTrace)
                .subscribeOn(Schedulers.newThread())
                .subscribe();
    }

    private void broadcast(DatagramPacket datagramPacket) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        socket.send(datagramPacket);
    }

    private DatagramPacket buildBroadcastPackaet(RMIServiceInfo info) throws UnsupportedEncodingException {
        byte[] infoByteString = info.toJson().getBytes("UTF-8");
        return new DatagramPacket(infoByteString, infoByteString.length, new InetSocketAddress(BROADCAST_PORT));
    }

    @Override
    public synchronized void stopAdvertiser() throws IOException {
        if(disposable == null) {
            return;
        }
        disposable.dispose();
    }
}
