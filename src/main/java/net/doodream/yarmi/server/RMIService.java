package net.doodream.yarmi.server;


import net.doodream.yarmi.Properties;
import net.doodream.yarmi.annotation.AdapterParam;
import net.doodream.yarmi.annotation.server.Service;
import net.doodream.yarmi.data.*;
import net.doodream.yarmi.net.ServiceAdapter;
import net.doodream.yarmi.net.session.BlobSession;
import net.doodream.yarmi.serde.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by innocentevil on 18. 5. 4.
 * generic request router for containing controller which handles request and returns response
 * , providing validity check for both request / response. it routes request message from client to the
 * targeting controller only for valid request and vice versa.
 *
 */
public class RMIService {

    private static final Logger Log = LoggerFactory.getLogger(RMIService.class);

    private HashMap<String, RMIController> controllerMap;
    private RMIServiceInfo serviceInfo;
    private ServiceAdapter adapter;
    private Converter converter;

    private RMIService() { }

    public static class Builder {
        private final RMIService service = new RMIService();

        public Builder adapter(ServiceAdapter adapter) {
            service.adapter = adapter;
            return this;
        }

        public Builder converter(Converter converter) {
            service.converter = converter;
            return this;
        }

        public Builder serviceInfo(RMIServiceInfo serviceInfo) {
            service.serviceInfo = serviceInfo;
            return this;
        }


        public Builder controllerMap(HashMap<String, RMIController> controllerMap) {
            service.controllerMap = controllerMap;
            return this;
        }

        public RMIService build() {
            // TODO: 19. 6. 6 check minimal service requirement fulfillment
            return service;
        }

    }


    /**
     *
     * @param cls
     * @param controllerImpls
     * @return
     */
    public static RMIService create(Class<?> cls, final Object ...controllerImpls) throws IllegalArgumentException {

        Service service = cls.getAnnotation(Service.class);
        final AdapterParam[] params = service.params();
        final Map<String, String> paramAsMap = new HashMap<>();
        for (AdapterParam param : params) {
            paramAsMap.put(param.key(), param.value());
        }

        try {
            final ServiceAdapter adapter = service.adapter().newInstance();
            final Converter converter = service.converter().newInstance();
            if(converter == null) {
                throw new IllegalArgumentException("converter can't be null");
            }

            adapter.configure(paramAsMap);

            final List<ControllerInfo> controllerInfos = new ArrayList<>();
            final HashMap<String, RMIController> controllerMap = new HashMap<>();

            for (Field field : cls.getDeclaredFields()) {
                if(RMIController.isValidController(field)) {
                    RMIController controller = RMIController.create(field, controllerImpls);
                    ControllerInfo controllerInfo = ControllerInfo.build(controller);
                    controllerInfos.add(controllerInfo);
                    buildControllerMap(controllerMap, controller);
                }
            }

            final RMIServiceInfo serviceInfo = RMIServiceInfo.builder()
                    .name(service.name())
                    .adapter(service.adapter())
                    .negotiator(service.negotiator())
                    .converter(service.converter())
                    .provider(service.provider())
                    .params(paramAsMap)
                    .controllerInfos(controllerInfos)
                    .version(Properties.getVersionString())
                    .build();

            return RMIService.builder()
                    .adapter(adapter)
                    .controllerMap(controllerMap)
                    .converter(converter)
                    .serviceInfo(serviceInfo)
                    .build();

        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("invalid parameter : no default constructor", e);
        }
    }

    private static Builder builder() {
        return new Builder();
    }


    /**
     *
     * @param cls service definition class
     * @return {@link RMIService} created from the service definition class
     * @throws IllegalAccessException constructor for components class is not accessible (e.g. no default constructor)
     * @throws InstantiationException fail to resolve components object (e.g. component is abstract class)
     * @throws InvocationTargetException exception caused at constructor of components
     */
    public static <T> RMIService create(Class<T> cls) throws IllegalArgumentException{
        return create(cls, new Object[0]);
    }

    /**
     * add controller into map which provides lookup for controller from endpoint hash
     * @param map map used to collect controller
     * @param controller controller to be collected
     */
    private static void buildControllerMap(final HashMap<String, RMIController> map, final RMIController controller) {
        for (String endpoint : controller.getEndpoints()) {
            map.put(endpoint, controller);
        }
    }

    /**
     * start listening for client connection over default network interface, while advertising service
     * @throws IOException server 측 네트워크 endpoint 생성의 실패 혹은 I/O 오류
     * @throws IllegalAccessError the error thrown when {@link ServiceAdapter} fails to resolve dependency object (e.g. negotiator,
     * @throws InstantiationException if dependent class represents an abstract class,an interface, an array class, a primitive type, or void;or if the class has no nullary constructor;
     */
    public void listen() throws IOException, IllegalAccessException, InstantiationException {
        // TODO: 18. 11. 19 start multiple service adapter  
        // TODO: 18. 11. 19 pass network parameter
        InetAddress localhost = InetAddress.getLocalHost();
        listen(localhost);
    }

    /**
     * start listening for client connection over given network interface, while advertising service
     * @param network address of network interface
     * @throws IllegalAccessException server 측 네트워크 endpoint 생성의 실패 혹은 I/O 오류
     * @throws IOException the error thrown when {@link ServiceAdapter} fails to resolve dependency object (e.g. negotiator,
     * @throws InstantiationException if dependent class represents an abstract class,an interface, an array class, a primitive type, or void;or if the class has no nullary constructor;
     */
    public void listen(InetAddress network) throws IllegalAccessException, IOException, InstantiationException {
        serviceInfo.setProxyFactoryHint(adapter.listen(serviceInfo, network, request -> {
            try {
                return routeRequest(request);
            } catch (IllegalAccessException | InvalidResponseException | IOException e) {
                return RMIError.INTERNAL_SERVER_ERROR.getResponse();
            }
        }));
    }

    /**
     * route {@link Request} to target controller
     * @param request @{@link Request} from the client
     * @return {@link Response} for the given {@link Request}
     * @throws IllegalAccessException if this {@code Method} object is enforcing Java language access control and the underlying method is inaccessible.
     * @throws InvalidResponseException {@link Response} from controller is not valid, refer {@link Response::validate}
     * @throws IOException I/O error in sending response to client
     */
    private Response routeRequest(Request request) throws IllegalAccessException, InvalidResponseException, IOException {
        if(!Request.isValid(request)) {
            return end(Response.from(RMIError.BAD_REQUEST), request);
        }

        Response response;
        RMIController controller = controllerMap.get(request.getEndpoint());

        if(controller != null) {
            try {
                Log.trace("handle request ({}) @ service", request.getNonce());
                response = controller.handleRequest(request, converter);
                if (response == null) {
                    response = Response.from(RMIError.NOT_IMPLEMENTED);
                }
                return end(response, request);
            } catch (InvocationTargetException e) {
                Log.error("InvocationError : {}", e);
                return end(Response.from(RMIError.INTERNAL_SERVER_ERROR), request);
            }
        }

        return end(Response.from(RMIError.NOT_FOUND), request);
    }

    /**
     *
     * @param res {@link Response} from controller
     * @param req {@link Request} from client
     * @return validated response which contains request information
     * @throws InvalidResponseException the {@link Response} is not valid type, meaning controller logic has some problem
     * @throws IOException problem in closing session for the {@link Request}
     */
    private Response end(Response res, Request req) throws InvalidResponseException, IOException {
        res.setNonce(req.getNonce());
        final BlobSession session = req.getSession();
        if(session != null) {
            session.close();
        }
        try {
            Response.validate(res);
        } catch (RuntimeException e) {
            InvalidResponseException exception = new InvalidResponseException(String.format("Invalid response for endpoint : %s", req.getEndpoint()));
            exception.initCause(e);
            throw exception;
        }

        res.setEndpoint(req.getEndpoint());
        return res;
    }

    /**
     * stop service and release system resources
     * @throws IOException problem occurred in closing advertising channel
     */
    public void stop() throws IOException {
        adapter.close();
    }

    public RMIServiceInfo getServiceInfo() {
        return serviceInfo;
    }
}
