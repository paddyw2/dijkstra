import java.util.*;

public class SendStateTimer extends TimerTask
{
    private Router router;

    public SendStateTimer(Router router)
    {
        this.router = router;
    }

    public void run()
    {
        // runs the send distance vector method
        router.sendNodeState();

    }
}
