package eu.arrowhead.client.datamanager.consumer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue; 

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpMethod;

import eu.arrowhead.client.library.ArrowheadService;
import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.dto.shared.OrchestrationFlags.Flag;
import eu.arrowhead.common.dto.shared.OrchestrationFormRequestDTO;
import eu.arrowhead.common.dto.shared.OrchestrationFormRequestDTO.Builder;
import eu.arrowhead.common.dto.shared.OrchestrationResponseDTO;
import eu.arrowhead.common.dto.shared.OrchestrationResultDTO;
import eu.arrowhead.common.dto.shared.ServiceQueryFormDTO;
//import eu.arrowhead.common.dto.shared.SenML; use when senml.java is merged
import eu.arrowhead.common.exception.ArrowheadException;

@SpringBootApplication
@ComponentScan(basePackages = {CommonConstants.BASE_PACKAGE}) //TODO: add custom packages if any
public class ConsumerMain implements ApplicationRunner {
    
    //=================================================================================================
	// members
	
    @Autowired
	private ArrowheadService arrowheadService;
    
	private final Logger logger = LogManager.getLogger( ConsumerMain.class );
    
    //=================================================================================================
	// methods

	//------------------------------------------------------------------------------------------------
    public static void main( final String[] args ) {
    	SpringApplication.run(ConsumerMain.class, args);
    }

    //-------------------------------------------------------------------------------------------------
    @Override
	public void run(final ApplicationArguments args) throws Exception {
		//SIMPLE EXAMPLE OF INITIATING AN ORCHESTRATION
    	
    	final Builder orchestrationFormBuilder = arrowheadService.getOrchestrationFormBuilder();
    	
    	final ServiceQueryFormDTO requestedService = new ServiceQueryFormDTO();
    	requestedService.setServiceDefinitionRequirement("test-service");
    	
    	orchestrationFormBuilder.requestedService(requestedService)
    							.flag(Flag.MATCHMAKING, false) //When this flag is false or not specified, then the orchestration response cloud contain more proper provider. Otherwise only one will be chosen if there is any proper.
    							.flag(Flag.OVERRIDE_STORE, false) //When this flag is false or not specified, then a Store Orchestration will be proceeded. Otherwise a Dynamic Orchestration will be proceeded.
    							.flag(Flag.TRIGGER_INTER_CLOUD, false); //When this flag is false or not specified, then orchestration will not look for providers in the neighbor clouds, when there is no proper provider in the local cloud. Otherwise it will. 
    	
    	final OrchestrationFormRequestDTO orchestrationRequest = orchestrationFormBuilder.build();
    	
    	OrchestrationResponseDTO response = null;
    	try {
    		response = arrowheadService.proceedOrchestration(orchestrationRequest);			
		} catch (final ArrowheadException ex) {
			//Handle the unsuccessful request as you wish!
		}
    	
    	// start consuming data forever
	boolean run = true;
	BlockingQueue<SenML[]> bq = new LinkedBlockingQueue<SenML[]>();

	Timer t = new Timer();
	t.schedule(new TimerTask(){
		@Override
		public void run() {
			final String jresp = arrowheadService.consumeServiceHTTP(String.class, HttpMethod.GET, "192.168.1.114", 8461, "/datamanager/historian/mulle-342/_mulle-342._temperature._http._tw.net", "HTTP-INSECURE-JSON", null, null);
			//System.out.println(jresp + "\n" );
			SenML[] sml = Utilities.fromJson(jresp, SenML[].class);
			bq.add(sml);
		}
	}, 0, 1000);

	while (run) {
		try {
			//Thread.sleep(10);
			SenML[] latest = (SenML[])bq.take();
			System.err.println("> " + Utilities.toJson(latest));
		} catch (InterruptedException ie) {}
	}
    	
    	if (response == null || response.getResponse().isEmpty()) {
    		//If no proper providers found during the orchestration process, then the response list will be empty. Handle the case as you wish!
    		logger.debug("Orchestration response is empty");
    		return;
    	} 
    	
	final OrchestrationResultDTO result = response.getResponse().get(0); //Simplest way of choosing a provider.

	final HttpMethod httpMethod = HttpMethod.GET;//Http method should be specified in the description of the service.
	final String address = result.getProvider().getAddress();
	final int port = result.getProvider().getPort();
	final String serviceUri = result.getServiceUri();
	final String interfaceName = result.getInterfaces().get(0).getInterfaceName(); //Simplest way of choosing an interface.
	String token = null;
	if (result.getAuthorizationTokens() != null) {
		token = result.getAuthorizationTokens().get(interfaceName); //Can be null when the security type of the provider is 'CERTIFICATE' or nothing.
	}
	final Object payload = null; //Can be null if not specified in the description of the service.

	final String consumedService = arrowheadService.consumeServiceHTTP(String.class, httpMethod, address, port, serviceUri, interfaceName, token, payload, "testkey", "testvalue");
	}
}
