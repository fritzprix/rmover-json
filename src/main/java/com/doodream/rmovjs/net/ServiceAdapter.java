package com.doodream.rmovjs.net;


import com.doodream.rmovjs.model.RMIServiceInfo;
import com.doodream.rmovjs.model.Request;
import com.doodream.rmovjs.model.Response;
import io.reactivex.functions.Function;

import java.io.IOException;

/**
 *  ServiceAdapter는 서비스의 기반 네트워크 계층에 대한 추상화를 제공한다.
 *  1.
 *
 *
 */
public interface ServiceAdapter {
    void listen(RMIServiceInfo serviceInfo, Function<Request, Response> requestHandler) throws IOException;
    ServiceProxyFactory getProxyFactory(RMIServiceInfo info);
    void close() throws IOException;
}