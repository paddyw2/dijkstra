import java.util.*;

public class UpdateRouteTimer extends TimerTask
{
    private Router router;

    public UpdateRouteTimer(Router router)
    {
        this.router = router;
    }
    public void run()
    {
        // runs the update router method
        router.updateNodeRoute();

    }
}
