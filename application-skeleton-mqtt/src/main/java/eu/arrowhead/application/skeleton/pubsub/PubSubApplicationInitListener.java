package eu.arrowhead.application.skeleton.pubsub;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import ai.aitia.arrowhead.application.library.ArrowheadService;
import ai.aitia.arrowhead.application.library.config.ApplicationInitListener;
import ai.aitia.arrowhead.application.library.util.ApplicationCommonConstants;
import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.core.CoreSystem;
import eu.arrowhead.common.exception.ArrowheadException;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.internal.security.SSLSocketFactoryFactory;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;

@Component
public class PubSubApplicationInitListener extends ApplicationInitListener implements MqttCallback {
	
	//=================================================================================================
	// members
	
	@Autowired
	private ArrowheadService arrowheadService;

	//@Value(CommonConstants.$APPLICATION_SYSTEM_NAME)
	@Value("${application_system_name}")
	private String systemName;

	//@Value(CommonConstants.$MQTT_BROKER_ADDRESS)
	@Value("${mqtt.broker.address}")
	private String brokerAddress;

	//@Value(CommonConstants.$MQTT_BROKER_PORT)
	@Value("${mqtt.broker.port}")
	private int brokerPort;

	//@Value(CommonConstants.$MQTT_BROKER_USERNAME)
	@Value("${mqtt.broker.username}")
	private String brokerUsername;
	
	//@Value(CommonConstants.$MQTT_BROKER_PASSWORD)
	@Value("${mqtt.broker.password}")
	private String brokerPassword;

	@Value(CommonConstants.$SERVER_SSL_ENABLED_WD)
	private boolean sslEnabled;
	
	MqttClient client = null;
	private final Logger logger = LogManager.getLogger(PubSubApplicationInitListener.class);
	
	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Override
	protected void customInit(final ContextRefreshedEvent event) {
		
        //TODO: implement here any custom behavior on application start up
        try {
            client = arrowheadService.connectMQTTBroker(this, brokerAddress, brokerPort, brokerUsername, brokerPassword, systemName);

            //Checking the availability of necessary core systems  add MQTT support here as well?
            //checkCoreSystemReachability(CoreSystem.SERVICEREGISTRY); add MQTT support here as well?
            //checkCoreSystemReachability(CoreSystem.AUTHORIZATION); add MQTT support here as well?
            client.subscribe("ah/pubsub/messages");
        } catch (Exception e) {
            client = null;
            throw new ArrowheadException("Could not connect to MQTT broker!\n" + e.toString());
        }
	}

	//-------------------------------------------------------------------------------------------------
	@Override
	public void customDestroy() {
        //TODO: implement here any custom behavior on application shut down
        if (client != null) {
            try {
                arrowheadService.disconnectMQTTBroker(client);
                arrowheadService.closeMQTTBroker(client);
            } catch(Exception e) {
            }
            client = null;
        }
    }
	
	//=================================================================================================
	// MQTT callback methods

    @Override
    public void connectionLost(Throwable t) {

        //TODO: implement here any custom behavior on broker disconnect, typically a reconnect after a short timeout
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {

        //TODO: implement here any custom behavior on incoming messages, such as filtering in topics etc
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

	//=================================================================================================
	// assistant methods
	
}
