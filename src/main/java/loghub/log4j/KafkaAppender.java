/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package loghub.log4j;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.apache.kafka.clients.producer.ProducerConfig.COMPRESSION_TYPE_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG;
import static org.apache.kafka.common.config.SaslConfigs.SASL_KERBEROS_SERVICE_NAME;

/**
 * A log4j appender that produces log messages to Kafka
 */
public class KafkaAppender extends AppenderSkeleton {

    private String brokerList;
    private String topic;
    private String compressionType;
    private String securityProtocol;
    private String sslTruststoreLocation;
    private String sslTruststorePassword;
    private String sslKeystoreType;
    private String sslKeystoreLocation;
    private String sslKeystorePassword;
    private String saslKerberosServiceName;
    private String clientJaasConfPath;
    private String kerb5ConfPath;

    private String serializerName = JavaSerializer.class.getName();
    private Serializer serializer;

    private String hostname =  null;
    private String application;
    private boolean locationInfo = false;

    private int retries;
    private int requiredNumAcks = Integer.MAX_VALUE;
    private boolean syncSend;
    private Producer<byte[], byte[]> producer;

    public Producer<byte[], byte[]> getProducer() {
        return producer;
    }

    public String getBrokerList() {
        return brokerList;
    }

    public void setBrokerList(String brokerList) {
        this.brokerList = brokerList;
    }

    public int getRequiredNumAcks() {
        return requiredNumAcks;
    }

    public void setRequiredNumAcks(int requiredNumAcks) {
        this.requiredNumAcks = requiredNumAcks;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public String getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public boolean getSyncSend() {
        return syncSend;
    }

    public void setSyncSend(boolean syncSend) {
        this.syncSend = syncSend;
    }

    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }

    public String getSslTruststoreLocation() {
        return sslTruststoreLocation;
    }

    public String getSecurityProtocol() {
        return securityProtocol;
    }

    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
    }

    public void setSslTruststoreLocation(String sslTruststoreLocation) {
        this.sslTruststoreLocation = sslTruststoreLocation;
    }

    public void setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
    }

    public void setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
    }

    public void setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
    }

    public void setSslKeystoreLocation(String sslKeystoreLocation) {
        this.sslKeystoreLocation = sslKeystoreLocation;
    }

    public void setSaslKerberosServiceName(String saslKerberosServiceName) {
        this.saslKerberosServiceName = saslKerberosServiceName;
    }

    public void setClientJaasConfPath(String clientJaasConfPath) {
        this.clientJaasConfPath = clientJaasConfPath;
    }

    public void setKerb5ConfPath(String kerb5ConfPath) {
        this.kerb5ConfPath = kerb5ConfPath;
    }

    public String getSslKeystoreLocation() {
        return sslKeystoreLocation;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    public String getSaslKerberosServiceName() {
        return saslKerberosServiceName;
    }

    public String getClientJaasConfPath() {
        return clientJaasConfPath;
    }

    public String getKerb5ConfPath() {
        return kerb5ConfPath;
    }

    public String getSerializer() {
        return serializerName;
    }

    /**
     * The class name of a implementation of a {@link Seralizer} interface.
     * @param serializer
     */
    public void setSerializer(String serializer) {
        this.serializerName = serializer;
    }

    /**
     * The <b>Hostname</b> take a string value. The default value is resolved using InetAddress.getLocalHost().getHostName().
     * It will be send in a custom hostname property.
     * @param hostname
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return value of the <b>Hostname</b> option.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * The <b>LocationInfo</b> option takes a boolean value. If true,
     * the information sent to the remote host will include location
     * information. By default no location information is sent to the server.
     */
    public void setLocationInfo(boolean locationInfo) {
        this.locationInfo = locationInfo;
    }

    /**
     * @return value of the <b>LocationInfo</b> option.
     */
    public boolean getLocationInfo() {
        return locationInfo;
    }

    /**
     * The <b>App</b> option takes a string value which should be the name of the 
     * application getting logged.
     * If property was already set (via system property), don't set here.
     */
    public void setApplication(String application) {
        this.application = application;
    }

    /**
     *  @return value of the <b>Application</b> option.
     */
    public String getApplication() {
        return application;
    }

    @Override
    public void activateOptions() {

        try {
            @SuppressWarnings("unchecked")
            Class<? extends Serializer> c = (Class<? extends Serializer>) getClass().getClassLoader().loadClass(serializerName);
            serializer = c.getConstructor().newInstance();
        } catch (ClassCastException | ClassNotFoundException | IllegalArgumentException | SecurityException | InstantiationException | IllegalAccessException 
                | NoSuchMethodException e) {
            errorHandler.error("failed to create serializer", e, ErrorCode.GENERIC_FAILURE);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                errorHandler.error("failed to create serializer", (Exception) e.getCause(), ErrorCode.GENERIC_FAILURE);
            } else {
                errorHandler.error("failed to create serializer", e, ErrorCode.GENERIC_FAILURE);
            }
        }

        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                errorHandler.error(e.getMessage(), e, ErrorCode.GENERIC_FAILURE);
            } 
        }

        if (brokerList == null) {
            throw new ConfigException("The bootstrap servers property should be specified");
        }
        if (topic == null) {
            throw new ConfigException("Topic must be specified by the Kafka log4j appender");
        }

        // check for config parameter validity
        Properties props = new Properties();
        props.put(BOOTSTRAP_SERVERS_CONFIG, brokerList);
        if (compressionType != null) {
            props.put(COMPRESSION_TYPE_CONFIG, compressionType);
        }
        if (requiredNumAcks != Integer.MAX_VALUE) {
            props.put(ACKS_CONFIG, Integer.toString(requiredNumAcks));
        }
        if (retries > 0) {
            props.put(RETRIES_CONFIG, retries);
        }
        if (securityProtocol != null) {
            props.put(SECURITY_PROTOCOL_CONFIG, securityProtocol);
        }
        if (securityProtocol != null && securityProtocol.contains("SSL") && sslTruststoreLocation != null &&
                sslTruststorePassword != null) {
            props.put(SSL_TRUSTSTORE_LOCATION_CONFIG, sslTruststoreLocation);
            props.put(SSL_TRUSTSTORE_PASSWORD_CONFIG, sslTruststorePassword);

            if (sslKeystoreType != null && sslKeystoreLocation != null &&
                    sslKeystorePassword != null) {
                props.put(SSL_KEYSTORE_TYPE_CONFIG, sslKeystoreType);
                props.put(SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreLocation);
                props.put(SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystorePassword);
            }
        }
        if (securityProtocol != null && securityProtocol.contains("SASL") && saslKerberosServiceName != null) {
            props.put(SASL_KERBEROS_SERVICE_NAME, saslKerberosServiceName);
            if (kerb5ConfPath != null) {
                System.setProperty("java.security.krb5.conf", kerb5ConfPath);
            }
            if (clientJaasConfPath != null) {
                System.setProperty("java.security.auth.login.config", clientJaasConfPath);
            }
        }

        props.put(KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        producer = getKafkaProducer(props);
        LogLog.debug("Kafka producer connected to " + brokerList);
        LogLog.debug("Logging for topic: " + topic);
    }


    /**
     * A separate method, to be used by junit's tests
     * @param props
     * @return a Kafka producer
     */
    protected Producer<byte[], byte[]> getKafkaProducer(Properties props) {
        return new KafkaProducer<>(props);
    }

    @Override
    protected void append(LoggingEvent event) {
        @SuppressWarnings("unchecked")
        Map<String, String> eventProps = event.getProperties();

        // The event is copied, because a host field is added in the properties
        LoggingEvent modifiedEvent = new LoggingEvent(event.getFQNOfLoggerClass(), event.getLogger(), event.getTimeStamp(), event.getLevel(), event.getMessage(),
                event.getThreadName(), event.getThrowableInformation(), event.getNDC(), locationInfo ? event.getLocationInformation() : null,
                        new HashMap<String,String>(eventProps));

        if (application != null) {
            modifiedEvent.setProperty("application", application);
        }
        modifiedEvent.setProperty("hostname", hostname);

        try {
            Future<RecordMetadata> response = producer.send(
                    new ProducerRecord<byte[], byte[]>(topic, serializer.objectToBytes(modifiedEvent)));
            if (syncSend) {
                response.get();
            }
        } catch (IOException e) {
            errorHandler.error("failed to serialize event", e, ErrorCode.GENERIC_FAILURE);
        } catch (InterruptedException | ExecutionException e) {
            errorHandler.error("failed to send event", e, ErrorCode.FLUSH_FAILURE);
        }
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            producer.close();
        }
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

}
