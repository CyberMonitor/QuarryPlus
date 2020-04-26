package com.yogpc.qp.test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import net.minecraft.data.DirectoryCache;
import net.minecraft.data.IDataProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

public class Starter implements IDataProvider {
    private static final Starter INSTANCE = new Starter();
    private static final Logger LOGGER = LogManager.getLogger("QuarryPlus/TestExecutor");

    public static Starter getInstance() {
        return INSTANCE;
    }

    public static void startTest() {
        LOGGER.info("Hello test");
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                selectPackage("com.kotori316.test_qp"),
                selectPackage("com.yogpc.qp.test")
            )
            .build();

        Launcher launcher = LauncherFactory.create();

        // Register a listener of your choice
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);

        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        // Do something with the TestExecutionSummary.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        summary.printTo(new PrintWriter(stream));
        LOGGER.info(stream.toString());
        summary.getFailures().stream()
            .map(TestExecutionSummary.Failure::getException)
            .forEach(t -> LOGGER.fatal("Test failed.", t));
    }

    @Override
    public void act(DirectoryCache cache) {
        startTest();
    }

    @Override
    public String getName() {
        return "QuarryPlus Test";
    }
}
