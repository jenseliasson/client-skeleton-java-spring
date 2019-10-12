package eu.arrowhead.client.skeleton.publisher;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import eu.arrowhead.client.library.ArrowheadService;
import eu.arrowhead.client.library.config.ApplicationInitListener;
import eu.arrowhead.client.library.util.ClientCommonConstants;
import eu.arrowhead.client.skeleton.publisher.event.PresetEventType;
import eu.arrowhead.client.skeleton.publisher.security.PublisherSecurityConfig;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.core.CoreSystem;
import eu.arrowhead.common.dto.shared.EventPublishRequestDTO;
import eu.arrowhead.common.dto.shared.SystemRequestDTO;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.AuthException;
import eu.arrowhead.common.exception.BadPayloadException;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.common.exception.UnavailableServerException;

@Component
public class PublisherApplicationInitListener extends ApplicationInitListener {
	
	//=================================================================================================
	// members
	
	@Autowired
	private ArrowheadService arrowheadService;
	
	@Autowired
	private PublisherSecurityConfig publisherSecurityConfig;
	
	@Value(ClientCommonConstants.$TOKEN_SECURITY_FILTER_ENABLED_WD)
	private boolean tokenSecurityFilterEnabled;
	
	@Value(ClientCommonConstants.$CLIENT_SYSTEM_NAME)
	private String clientSystemName;
	
	@Value(ClientCommonConstants.$CLIENT_SERVER_ADDRESS_WD)
	private String clientSystemAddress;
	
	@Value(ClientCommonConstants.$CLIENT_SERVER_PORT_WD)
	private int clientSystemPort;
	
	private final Logger logger = LogManager.getLogger(PublisherApplicationInitListener.class);
	
	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Override
	protected void customInit(final ContextRefreshedEvent event) {

		//Checking the availability of necessary core systems
		checkCoreSystemReachability(CoreSystem.SERVICE_REGISTRY);
		if (tokenSecurityFilterEnabled) {
			checkCoreSystemReachability(CoreSystem.AUTHORIZATION);			

			//Initialize Arrowhead Context
			arrowheadService.updateCoreServiceURIs(CoreSystem.AUTHORIZATION);			
		}		
		
		setTokenSecurityFilter();
		
		if ( arrowheadService.echoCoreSystem( CoreSystem.EVENT_HANDLER ) ) {
			
			arrowheadService.updateCoreServiceURIs( CoreSystem.EVENT_HANDLER );	
			
			publishInitStartedEvent();
		}
		
		//TODO: implement here any custom behavior on application start up
	}

	//-------------------------------------------------------------------------------------------------
	@Override
	public void customDestroy() {
		//TODO: implement here any custom behavior on application shout down
	}
	
	//=================================================================================================
	// assistant methods

	//-------------------------------------------------------------------------------------------------
	private void setTokenSecurityFilter() {
		if(!tokenSecurityFilterEnabled) {
			logger.info("TokenSecurityFilter in not active");
		} else {
			final PublicKey authorizationPublicKey = arrowheadService.queryAuthorizationPublicKey();
			if (authorizationPublicKey == null) {
				throw new ArrowheadException("Authorization public key is null");
			}
			
			KeyStore keystore;
			try {
				keystore = KeyStore.getInstance(sslProperties.getKeyStoreType());
				keystore.load(sslProperties.getKeyStore().getInputStream(), sslProperties.getKeyStorePassword().toCharArray());
			} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException ex) {
				throw new ArrowheadException(ex.getMessage());
			}			
			final PrivateKey publisherPrivateKey = Utilities.getPrivateKey(keystore, sslProperties.getKeyPassword());

			publisherSecurityConfig.getTokenSecurityFilter().setAuthorizationPublicKey(authorizationPublicKey);
			publisherSecurityConfig.getTokenSecurityFilter().setMyPrivateKey(publisherPrivateKey);
		}
	}

	//-------------------------------------------------------------------------------------------------	
	//Sample implementation of event publishing at application init time
	/**
	 * Sends a http(s) 'publish' request to Event Handler Core System.
	 * 
	 * @throws AuthException when you are not authorized by Event Handler Core System
	 * @throws BadPayloadException when the payload couldn't be validated by Event Handler Core System 
	 * @throws InvalidParameterException when the payload content couldn't be validated by Event Handler Core System
	 * @throws ArrowheadException when internal server error happened at Event Handler Core System
	 * @throws UnavailableServerException when Event Handler Core System is not available
	 */
	private void publishInitStartedEvent() {
		logger.debug( "publishInitStartedEvent started..." );
		
		final String eventType = PresetEventType.START_INIT.getEventTypeName();
		
		final SystemRequestDTO source = new SystemRequestDTO();
		source.setSystemName( clientSystemName );
		source.setAddress( clientSystemAddress );
		source.setPort( clientSystemPort );
		source.setAuthenticationInfo( Base64.getEncoder().encodeToString( arrowheadService.getMyPublicKey().getEncoded() ) );
		
		final Map<String,String> metadata = null;
		
		final String payload = "InitStarted";
		
		final String timeStamp = Utilities.convertZonedDateTimeToUTCString( ZonedDateTime.now() );
		
		final EventPublishRequestDTO publishRequestDTO = new EventPublishRequestDTO(
				eventType, 
				source, 
				metadata, 
				payload, 
				timeStamp);
		
		arrowheadService.publishToEventHandler( publishRequestDTO );
				
	}

}