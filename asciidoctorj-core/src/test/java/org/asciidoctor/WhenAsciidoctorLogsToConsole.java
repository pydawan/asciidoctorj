package org.asciidoctor;

import org.asciidoctor.ast.Cursor;
import org.asciidoctor.internal.JRubyAsciidoctor;
import org.asciidoctor.log.LogHandler;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.TestLogHandlerService;
import org.asciidoctor.util.ClasspathResources;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.asciidoctor.OptionsBuilder.options;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class WhenAsciidoctorLogsToConsole {

    @Rule
    public ClasspathResources classpath = new ClasspathResources();

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private Asciidoctor asciidoctor;

    @Before
    public void before() {
        asciidoctor = JRubyAsciidoctor.create();
        TestLogHandlerService.clear();
    }

    @After
    public void cleanup() throws IOException {
        LogManager.getLogManager().readConfiguration();
    }

    @Test
    public void shouldRedirectToJUL() throws Exception {
        final MemoryLogHandler memoryLogHandler = registerMemoryLogHandler();

        File inputFile = classpath.getResource("documentwithnotexistingfile.adoc");
        String renderContent = asciidoctor.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        File expectedFile = new File(inputFile.getParent(), "documentwithnotexistingfile.html");
        expectedFile.delete();

        assertEquals(4, memoryLogHandler.getLogRecords().size());
        assertThat(memoryLogHandler.getLogRecords().get(0).getMessage(),
            both(containsString("include file not found"))
                .and(containsString("documentwithnotexistingfile.adoc: line 3")));
    }

    private MemoryLogHandler registerMemoryLogHandler() {
        final Logger logger = Logger.getLogger("asciidoctor");
        final MemoryLogHandler handler = new MemoryLogHandler();
        logger.addHandler(handler);
        return handler;
    }

    @Test
    public void shouldNotifyLogHandler() throws Exception {

        final List<LogRecord> logRecords = new ArrayList<>();


        final LogHandler logHandler = new LogHandler() {
            @Override
            public void log(LogRecord logRecord) {
                logRecords.add(logRecord);
            }
        };
        asciidoctor.registerLogHandler(logHandler);

        File inputFile = classpath.getResource("documentwithnotexistingfile.adoc");
        String renderContent = asciidoctor.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        File expectedFile = new File(inputFile.getParent(), "documentwithnotexistingfile.html");
        expectedFile.delete();

        assertEquals(4, logRecords.size());
        assertThat(logRecords.get(0).getMessage(), containsString("include file not found"));
        final Cursor cursor = logRecords.get(0).getCursor();
        assertThat(cursor.getDir().replace('\\', '/'), is(inputFile.getParent().replace('\\', '/')));
        assertThat(cursor.getFile(), is(inputFile.getName()));
        assertThat(cursor.getLineNumber(), is(3));

        for (LogRecord logRecord: logRecords) {
            assertThat(logRecord.getCursor(), not(nullValue()));
            assertThat(logRecord.getCursor().getFile(), not(nullValue()));
            assertThat(logRecord.getCursor().getDir(), not(nullValue()));
        }

    }

    @Test
    public void shouldLogInvalidRefs() throws Exception {

        final List<LogRecord> logRecords = new ArrayList<>();

        final LogHandler logHandler = new LogHandler() {
            @Override
            public void log(LogRecord logRecord) {
                logRecords.add(logRecord);
            }
        };
        asciidoctor.registerLogHandler(logHandler);

        File inputFile = classpath.getResource("documentwithinvalidrefs.adoc");
        String renderContent = asciidoctor.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .toFile(false)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        assertThat(logRecords, hasSize(1));
        assertThat(logRecords.get(0).getMessage(), containsString("invalid reference: invalidref"));
        final Cursor cursor = logRecords.get(0).getCursor();
        assertThat(cursor, is(nullValue()));
    }

    @Test
    public void shouldOnlyNotifyFromRegisteredAsciidoctor() throws Exception {

        final List<LogRecord> logRecords = new ArrayList<>();

        final Asciidoctor secondInstance = Asciidoctor.Factory.create();

        final LogHandler logHandler = new LogHandler() {
            @Override
            public void log(LogRecord logRecord) {
                logRecords.add(logRecord);
            }
        };
        // Register at first instance!
        asciidoctor.registerLogHandler(logHandler);

        // Now render via second instance and check that there is no notification
        File inputFile = classpath.getResource("documentwithnotexistingfile.adoc");
        String renderContent1 = secondInstance.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        File expectedFile1 = new File(inputFile.getParent(), "documentwithnotexistingfile.html");
        expectedFile1.delete();

        assertEquals(0, logRecords.size());

        // Now render via first instance and check that notifications appeared.
        String renderContent = asciidoctor.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        File expectedFile2 = new File(inputFile.getParent(), "documentwithnotexistingfile.html");
        expectedFile2.delete();

        assertEquals(4, logRecords.size());
        assertThat(logRecords.get(0).getMessage(), containsString("include file not found"));
        final Cursor cursor = (Cursor) logRecords.get(0).getCursor();
        assertThat(cursor.getDir().replace('\\', '/'), is(inputFile.getParent().replace('\\', '/')));
        assertThat(cursor.getFile(), is(inputFile.getName()));
        assertThat(cursor.getLineNumber(), is(3));
    }

    @Test
    public void shouldNoLongerNotifyAfterUnregisterOnlyNotifyFromRegisteredAsciidoctor() throws Exception {

        final List<LogRecord> logRecords = new ArrayList<>();

        final LogHandler logHandler = new LogHandler() {
            @Override
            public void log(LogRecord logRecord) {
                logRecords.add(logRecord);
            }
        };
        asciidoctor.registerLogHandler(logHandler);

        File inputFile = classpath.getResource("documentwithnotexistingfile.adoc");
        String renderContent = asciidoctor.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        File expectedFile = new File(inputFile.getParent(), "documentwithnotexistingfile.html");
        expectedFile.delete();

        assertEquals(4, logRecords.size());
        logRecords.clear();

        asciidoctor.unregisterLogHandler(logHandler);

        asciidoctor.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        File expectedFile2 = new File(inputFile.getParent(), "documentwithnotexistingfile.html");
        expectedFile2.delete();
        assertEquals(0, logRecords.size());

    }

    @Test
    public void shouldNotifyLogHandlerService() throws Exception {

        File inputFile = classpath.getResource("documentwithnotexistingfile.adoc");
        String renderContent = asciidoctor.renderFile(inputFile,
            options()
                .inPlace(true)
                .safe(SafeMode.SERVER)
                .attributes(
                    AttributesBuilder.attributes().allowUriRead(true))
                .asMap());

        File expectedFile = new File(inputFile.getParent(), "documentwithnotexistingfile.html");
        expectedFile.delete();

        final List<LogRecord> logRecords = TestLogHandlerService.getLogRecords();
        assertThat(logRecords, hasSize(4));
        assertThat(logRecords.get(0).getMessage(), containsString("include file not found"));
        final Cursor cursor = logRecords.get(0).getCursor();
        assertThat(cursor.getDir().replace('\\', '/'), is(inputFile.getParent().replace('\\', '/')));
        assertThat(cursor.getFile(), is(inputFile.getName()));
        assertThat(cursor.getLineNumber(), is(3));

        for (LogRecord logRecord: logRecords) {
            assertThat(logRecord.getCursor(), not(nullValue()));
            assertThat(logRecord.getCursor().getFile(), not(nullValue()));
            assertThat(logRecord.getCursor().getDir(), not(nullValue()));
        }
    }
}
