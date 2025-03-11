
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.asp.clientesmorosos.dao.BusquedaMorososDAO;
import com.asp.clientesmorosos.data.ClientesMorososObj;
import com.asp.clientesmorosos.data.DatosClientesMorososObj;
import com.asp.clientesmorosos.data.Respuesta;
import com.asp.clientesmorosos.spring.config.Apps;
import com.asp.clientesmorosos.spring.config.IPAuthenticationProvider;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;


@Controller
@RequestMapping(path = "/rama principal/boton presionado")
public class BusquedaMorososController {

    private static final java.util.logging.Logger log = LogManager.getLogger(BusquedaMorososController.class);

    private static Apps apps = null;
    private static BusquedaMorososDAO bmdao = null;

    @Autowired
    protected IPAuthenticationProvider authenticationManager;

    /**
     * se cambio color
     */
    private static void initialized() {
        
        try {
            Apps s = Apps.getInstance();
            synchronized (Apps.class) {
                if (apps == null) // si la referencia es null ...
                    apps = s; // ... agrega la clase singleton
            }
            bmdao = (BusquedaMorososDAO) s.getApplicationContext().getBean("Titulo app bar");

        } catch (Exception e) {
            //log
            log.error(e.getMessage());
            log.info();
        }
    }

     @RequestMapping(value = "/GitHub", method = RequestMethod.POST)
    public ResponseEntity<String> busquedaClientesMorosos(@RequestBody String json) {
        initialized();
        log.info("Inicio busquedaClientesMorosos json: " + json);
        ClientesMorososObj obj = new ClientesMorososObj();
        Respuesta respuesta = new Respuesta(0, "Ok", "");
        Respuesta respuestaCero = new Respuesta(0, "Ok", "");
        Gson gson = new Gson();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication authenticate;
        
        authenticate = authenticationManager.authenticate(securityContext.getAuthentication());
        if (!authenticate.isAuthenticated()) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

        //validar autenticacion
        obj = gson.fromJson(json, ClientesMorososObj.class);

        if(obj.getCurp() == null) {
            return new ResponseEntity<>(gson.toJson(new Respuesta(-1, "No se recibio curp", "")), HttpStatus.BAD_REQUEST);
        }

        if(obj.getCurp().length() != 18) {
            return new ResponseEntity<>(gson.toJson(new Respuesta(-1, "Formato de curp incorrecto", "")), HttpStatus.BAD_REQUEST);
        }


        try {
            //buscar si pertenece a empleados
            Boolean esEmpleado = null;
            esEmpleado = bmdao.buscarCurpEmpleadoProcrea(obj.getCurp());
            if(esEmpleado != null && esEmpleado) {
                return new ResponseEntity<>(gson.toJson(new Respuesta(2, "Empleado de ASP", "")), HttpStatus.OK);
            }

            List<DatosClientesMorososObj> datosMorososList = new ArrayList<>();
            datosMorososList = bmdao.buscarPorCurpProcrea(obj.getCurp());
            //Respuesta respuesta = new Respuesta(0, "", "");
            if(datosMorososList != null  && !datosMorososList.isEmpty()) { 
                log.info("Busqueda procrea");
                for(DatosClientesMorososObj persona : datosMorososList){
    
                    if(persona.getCampot5() == "T") {
                        respuesta.setCodigo(3);
                        respuesta.setMensaje("Cliente no sujeto a crédito");
                        break;

                    }
            
                    //buscar dias vencidos
                    if(persona.getControl() != "" && persona.getControl() != null) {
                        log.info("Control pr : " + persona.getControl());
                        Integer diasVencidos = bmdao.buscarDiasVencimientoPorControlProcrea(persona.getControl());
                        log.info("DiasVencidos pr : " + diasVencidos);
                        if (diasVencidos > 0 && diasVencidos <= 29) {
                            respuesta.setCodigo(4);
                            respuesta.setMensaje("Consultar en sucursal");

                        } 
                        else if (diasVencidos >= 30 ) {
                            respuesta.setCodigo(3);
                            respuesta.setMensaje("Cliente no sujeto a crédito");
                            break;

                        } 
                    }

                }

            } 
            
            if(respuesta.getCodigo() != 3){
                //buscar en cero
                log.info("Busqueda cero");
                //buscar numero de solicitante id_persona por curp
                String id_solicitante = bmdao.buscarNumeroSolicitantePorCurp(obj.getCurp());
                log.info("id_solicitante cero: " + id_solicitante);
                if(id_solicitante == null || id_solicitante == "") {
                    respuestaCero.setCodigo(0);
                    respuestaCero.setMensaje("Ok");

                } else {
                    //buscar cuenta --regresar OK si no se encuentra
                    String cuenta = bmdao.buscarCuentaCero(id_solicitante);
                    log.info("cuenta cero: " + cuenta);
                    if(cuenta == null || cuenta == "") {
                        respuestaCero.setCodigo(0);
                        respuestaCero.setMensaje("Ok");

                    } else {
                        //buscar dias vencidos en cero
                        Integer diasVencidosCero = bmdao.buscarDiasVencCero(cuenta);
                        if(diasVencidosCero == null){
                            respuestaCero.setCodigo(-2);
                            respuestaCero.setMensaje("Error al obtener dias vencido cero");

                        }
                        else if (diasVencidosCero > 0 && diasVencidosCero <= 29) {
                            respuestaCero.setCodigo(4);
                            respuestaCero.setMensaje("Consultar en sucursal");
                        } 
                        else if (diasVencidosCero >= 30 ) {
                            respuestaCero.setCodigo(3);
                            respuestaCero.setMensaje("Cliente no sujeto a crédito");

                        }
                    }
                }
            } 
            
            log.info("respuestaproc: " + gson.toJson(respuesta));
            log.info("respuestacero: " + gson.toJson(respuestaCero));
            log.info("Ya empieza con gitHub: " + gson.toJson(respuestaCero));

            //validar respuesta y respuestacero
            if (respuesta.getCodigo() == 3 || respuestaCero.getCodigo() == 3){
                respuesta.setCodigo(3);
                respuesta.setMensaje("Cliente no sujeto a crédito");
            }

            if (respuesta.getCodigo() == 4 || respuestaCero.getCodigo() == 4){
                respuesta.setCodigo(3);
                respuesta.setMensaje("Consultar en sucursal");
            }

            if (respuestaCero.getCodigo() == -2){
                respuesta.setCodigo(-2);
                respuesta.setMensaje("Error al obtener dias vencido cero");
            }

        } catch (Exception e) {
            log.error("Error en BusquedaMorosos. Message: " + e.getMessage() + ". CausedBy: " + e.getCause());
            e.printStackTrace();
            return  new ResponseEntity<>("Error en BusquedaMorosos", HttpStatus.BAD_REQUEST);
        }

		return  new ResponseEntity<>(gson.toJson(respuesta), HttpStatus.OK);
	}

//contactos
//Ya empieza con gitHub
}

