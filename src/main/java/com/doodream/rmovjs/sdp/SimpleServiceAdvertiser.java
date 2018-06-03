package com.doodream.rmovjs.sdp;

import com.doodream.rmovjs.model.RMIServiceInfo;
import com.doodream.rmovjs.serde.Converter;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.concurrent.TimeUnit;

/**
 * this class provices simple service advertising capability whose intented use is testing though,
 * can be used a simple service discovery scenario.
 *
 * advertiser start to advertise its RMIServiceInfo with broadcasting datagram socket
 */
public class SimpleServiceAdvertiser implements ServiceAdvertiser {

    private Logger Log = LogManager.getLogger(SimpleServiceDiscovery.class);
    public static final int BROADCAST_PORT = 3041;
    private Disposable disposable;


    @Override
    public synchronized void startAdvertiser(RMIServiceInfo info, Converter converter, boolean block) throws IOException {

        Observable<DatagramPacket> packetObservable = Observable.interval(0L, 3L, TimeUnit.SECONDS)
                .map(aLong -> info)
                .map(i -> buildBroadcastPacket(i, converter))
                .doOnNext(this::broadcast)
                .doOnError(Throwable::printStackTrace);

        if(!block) {
            disposable = packetObservable.subscribeOn(Schedulers.io()).subscribe();
            return;
        }
        packetObservable.blockingSubscribe();
    }

    private void broadcast(DatagramPacket datagramPacket) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setBroadcast(true);
        socket.send(datagramPacket);
        socket.close();
    }

    private DatagramPacket buildBroadcastPacket(RMIServiceInfo info, Converter converter) throws UnsupportedEncodingException, UnknownHostException {
        byte[] infoByteString = converter.convert(info);
//        return new DatagramPacket(infoByteString, infoByteString.length, Inet4Address.getByName("255.255.255.255"), BROADCAST_PORT);
        return new DatagramPacket(infoByteString, infoByteString.length, new InetSocketAddress(BROADCAST_PORT));

    }

    @Override
    public synchronized void stopAdvertiser() {
        if(disposable == null) {
            return;
        }
        disposable.dispose();
    }

}
