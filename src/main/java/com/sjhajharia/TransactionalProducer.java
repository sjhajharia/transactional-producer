package com.sjhajharia;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.Producer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;

import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.TopicExistsException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Collections;
import java.util.Scanner;

public class TransactionalProducer {

    public static final String TOPIC_NAME = "transaction-topic";
    public static final int NUM_PARTITIONS = 1;
    public static final short REP_FACTOR = 3;

    // Load Confluent Cloud properties from config file
    public static Properties loadConfig(final String configFile) throws IOException {
        if (!Files.exists(Paths.get(configFile))) {
            throw new IOException(configFile + " not found.");
        }
        final Properties cfg = new Properties();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            cfg.load(inputStream);
        }
        return cfg;
    }

    // Create topic in Confluent Cloud
    public static void createTopic(final Properties cloudConfig) {
        final NewTopic newTopic = new NewTopic(TOPIC_NAME, NUM_PARTITIONS, REP_FACTOR);
        try (final AdminClient adminClient = AdminClient.create(cloudConfig)) {
            adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
        } catch (final InterruptedException | ExecutionException e) {
            // Ignore if TopicExistsException, which may be valid if topic exists
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new RuntimeException(e);
            }
        }
    }


    public static void main(final String[] args) throws IOException, URISyntaxException {
        Scanner in = new Scanner(System.in);

        Path configFile = new File(TransactionalProducer.class.getClassLoader().getResource("cluster.config").toURI()).toPath();
        System.out.println("PATH: " + configFile);

        // Load properties from a local configuration file
        // Create the configuration file (e.g. at '$HOME/.confluent/java.config') with configuration parameters
        // to connect to your Kafka cluster, which can be on your local host, Confluent Cloud, or any other cluster.
        // Follow these instructions to create this file: https://docs.confluent.io/platform/current/tutorials/examples/clients/docs/java.html
        final Properties props = loadConfig(configFile.toString());

        // Create topic if needed
        createTopic(props);

        // Add additional properties.
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "t01");
        props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "30000");

        Producer<String, String> producer = new KafkaProducer<>(props);

        producer.initTransactions();

        try {
            producer.beginTransaction();
            System.out.println("*** Begin Transaction ***");
            System.out.printf("*** transactional.id %s ***\n", props.get("transactional.id"));

            for (int i = 0; i < 10; i++) {
                double randomDouble = Math.random();
                int randomNum = (int) (randomDouble * 10);
                //int num = i*i;
                producer.send(new ProducerRecord<>("transaction-topic", Integer.toString(i),
                        Integer.toString(randomNum)));
                System.out.printf("Sent %s:%s\n", i, randomNum);
                in.nextLine();
            }
            producer.commitTransaction();
            System.out.println("*** Commit Transaction ***");
        }
        catch (ProducerFencedException e) {
            System.out.println("PRODUCER FENCED");
            producer.close();
        }
        catch (OutOfOrderSequenceException | AuthorizationException e) {
            // We can't recover from these exceptions, so our only option is to close the producer and exit.
            System.out.println(e.toString());
            producer.close();
        }
        catch (KafkaException e) {
            // For all other exceptions, just abort the transaction and try again.
            System.out.println(e.toString());
            producer.abortTransaction();
        }
        in.close();
        producer.close();
    }

}