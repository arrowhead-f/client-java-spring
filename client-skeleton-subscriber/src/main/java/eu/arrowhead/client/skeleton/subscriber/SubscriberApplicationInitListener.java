package eu.arrowhead.client.skeleton.subscriber;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Base64;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import eu.arrowhead.client.library.ArrowheadService;
import eu.arrowhead.client.library.config.ApplicationInitListener;
import eu.arrowhead.client.library.util.ClientCommonConstants;
import eu.arrowhead.client.skeleton.subscriber.constants.SubscriberConstants;
import eu.arrowhead.client.skeleton.subscriber.constants.SubscriberDefaults;
import eu.arrowhead.client.skeleton.subscriber.security.SubscriberSecurityConfig;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.core.CoreSystem;
import eu.arrowhead.common.dto.shared.SubscriptionRequestDTO;
import eu.arrowhead.common.dto.shared.SystemRequestDTO;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.InvalidParameterException;

@Component
public class SubscriberApplicationInitListener extends ApplicationInitListener {
	
	//=================================================================================================
	// members
	
	@Autowired
	private ArrowheadService arrowheadService;
	
	@Autowired
	private SubscriberSecurityConfig subscriberSecurityConfig;
	
	@Value(ClientCommonConstants.$TOKEN_SECURITY_FILTER_ENABLED_WD)
	private boolean tokenSecurityFilterEnabled;
	
	@Value( SubscriberConstants.$PRESET_NOTIFICATION_URI_WD )
	private String presetNotificationUris;
	
	@Value( SubscriberConstants.$PRESET_EVENT_TYPES_WD )
	private String presetEvents;
	
	@Value(ClientCommonConstants.$CLIENT_SYSTEM_NAME)
	private String clientSystemName;
	
	@Value(ClientCommonConstants.$CLIENT_SERVER_ADDRESS_WD)
	private String clientSystemAddress;
	
	@Value(ClientCommonConstants.$CLIENT_SERVER_PORT_WD)
	private int clientSystemPort;
	
	private final Logger logger = LogManager.getLogger(SubscriberApplicationInitListener.class);
	
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
		
		setNotificationFilter();			

		
		if ( arrowheadService.echoCoreSystem( CoreSystem.EVENT_HANDLER ) ) {
			
			arrowheadService.updateCoreServiceURIs( CoreSystem.EVENT_HANDLER );	
			subscribeToPresetEvents();
			
		}
		
		//TODO: implement here any custom behavior on application start up
	}


	//-------------------------------------------------------------------------------------------------
	@Override
	public void customDestroy() {
		 
		if( presetEvents == null) {
			
			logger.info("No preset events to unsubscribe.");
		} else {
			
			final String[] eventTypes = presetEvents.split(",");
			
			final SystemRequestDTO subscriber = new SystemRequestDTO();
			subscriber.setSystemName( clientSystemName );
			subscriber.setAddress( clientSystemAddress );
			subscriber.setPort( clientSystemPort );
			subscriber.setAuthenticationInfo( Base64.getEncoder().encodeToString( arrowheadService.getMyPublicKey().getEncoded()) );
			
			
			for ( final String eventType : eventTypes ) {
				
				arrowheadService.unsubscribeFromEventHandler(eventType, clientSystemName, clientSystemAddress, clientSystemPort);
				
			}
		}
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
			final PrivateKey subscriberPrivateKey = Utilities.getPrivateKey(keystore, sslProperties.getKeyPassword());

			subscriberSecurityConfig.getTokenSecurityFilter().setAuthorizationPublicKey(authorizationPublicKey);
			subscriberSecurityConfig.getTokenSecurityFilter().setMyPrivateKey(subscriberPrivateKey);
		}
	}

	//-------------------------------------------------------------------------------------------------
	private void subscribeToPresetEvents() {
		if( presetEvents == null) {
			
			logger.info("No preset events to subscribe.");
		} else {
			
			final String[] eventTypes = presetEvents.split(",");
			
			final SystemRequestDTO subscriber = new SystemRequestDTO();
			subscriber.setSystemName( clientSystemName );
			subscriber.setAddress( clientSystemAddress );
			subscriber.setPort( clientSystemPort );
			subscriber.setAuthenticationInfo( Base64.getEncoder().encodeToString( arrowheadService.getMyPublicKey().getEncoded()) );
			
			
			for ( final String eventType : eventTypes ) {
				
				arrowheadService.unsubscribeFromEventHandler(eventType, clientSystemName, clientSystemAddress, clientSystemPort);
				
			}
			
			for ( final String eventType : eventTypes ) {
				
				final SubscriptionRequestDTO subscription = new SubscriptionRequestDTO(
						eventType.toUpperCase(), 
						subscriber, 
						null, 
						SubscriberDefaults.DEFAULT_EVENT_NOTIFICATION_BASE_URI, 
						false, 
						null, 
						null, 
						null);
				
				try {
					arrowheadService.subscribeToEventHandler( subscription );
				
				} catch ( InvalidParameterException ex) {
					
					if( ex.getMessage().contains( "Subscription violates uniqueConstraint rules" )) {
						
						logger.debug("Subscription is allready in DB");
					}
				} catch ( Exception ex) {
					
					logger.debug("Could not subscribe to EventType: " + subscription.getEventType());
				} 
				
			}
		}
	}
	
	//-------------------------------------------------------------------------------------------------
	private void setNotificationFilter() {
		logger.debug( "setNotificationFilter started..." );
		
		if( presetNotificationUris.isEmpty() ) {
			
			logger.info("TokenSecurityFilter in not active");
		
		} else {
			
			final String[] notificationUris = presetNotificationUris.split( "," );

			subscriberSecurityConfig.getNotificationFilter().setNotificationUris( notificationUris );
			subscriberSecurityConfig.getNotificationFilter().setServerCN( arrowheadService.getServerCN() );

		}
	}
}