import java.io.PrintWriter;
import java.util.Arrays;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Standalone JUnit launcher for verify.ps1; not part of the mod or its source manifest. */
public final class RunAchievementTests
{
    public static void main(String[] args)
    {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(Arrays.stream(args).map(DiscoverySelectors::selectClass).toList()).build();
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        var output = new PrintWriter(System.out, true);
        summary.printTo(output);
        summary.printFailuresTo(output);
        if (summary.getTestsFoundCount() == 0 || summary.getTotalFailureCount() != 0) System.exit(1);
    }
}
