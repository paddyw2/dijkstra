import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.Math;

/**
 * Router Class
 * 
 * Router implements Dijkstra's algorithm for computing the minumum distance to all nodes in the network
 * @author      XYZ
 * @version     1.0
 *
 */
public class Router {

    /**
        * Constructor to initialize the program 
        * 
        * @param peerip     IP address of other routers (we assume that all routers are running in the same machine)
        * @param routerid   Router ID
        * @param port       Router UDP port number
        * @param configfile Configuration file name
        * @param neighborupdate link state update interval - used to update router's link state vector to neighboring nodes
        * @param routeupdate    Route update interval - used to update route information using Dijkstra's algorithm
 
     */

    // variables
    private Timer timer;
    private DatagramSocket UDPSocket;
    private String peerip;
    private int port;
    private InetAddress IPAddress;
    private int[] distancevector;
    private int[] previousvector;
    private int routerid;
    private int noRouters;
    private LinkedList<int[]> nodeData;
    private LinkedList<Integer> neighbourPorts;
    private ArrayList<Integer> neighbourIds;

    public Router(String peerip, int routerid, int port, String configfile, int neighborupdate, int routeupdate) {
        // save data
        this.peerip = peerip;
        this.port = port;
        System.out.println("Port number: "+port);
        this.routerid = routerid;
        // create sender socket
        try {
            UDPSocket = new DatagramSocket(port);
        } catch (Exception e) {
            System.out.println("UDP socket init failure");
        }

        // create IP address
        IPAddress = null;
        try {
            IPAddress = InetAddress.getByName(peerip);
        } catch (Exception e) {
            System.out.println("Inet error");
            System.out.println(e.getMessage());
        }

        // create data structure, updated
        // when neighbour broadcast is
        // received
        nodeData = new LinkedList<int[]>();
        neighbourIds = new ArrayList<Integer>();
        neighbourPorts = new LinkedList<Integer>();
        timer = new Timer();

        // create node distance vector
        initializeVector(configfile);
    }

    public void initializeVector(String path)
    {
        byte[] fileData = readFile(path);
        String fileString = new String(fileData);
        String fileLines[] = fileString.split("\n");
        noRouters = Integer.parseInt(fileLines[0]);

        distancevector = new int[noRouters];
        previousvector = new int[noRouters];
        for(int i=0;i<noRouters;i++)
        {
            nodeData.add(null);
        }

        // initialize vector with infinity values
        for(int i=0;i<distancevector.length;i++) {
            distancevector[i] = 999;
            previousvector[i] = routerid;
        }
        // initialize self cost
        distancevector[routerid] = 0;

        // now initialize with neighbour values
        for(int i=1;i<fileLines.length;i++)
        {
            int routerId = Integer.parseInt(fileLines[i].substring(2,3));
            neighbourIds.add(routerId);
            neighbourPorts.add(Integer.parseInt(fileLines[i].substring(6,fileLines[i].length())));
            int idCost = Integer.parseInt(fileLines[i].substring(4,5));
            distancevector[routerId] = idCost;
        }

        nodeData.set(routerid, distancevector);
        
        // print initial vector state
        for(int val : distancevector)
        {
            System.out.print(val + " ");
        }
        System.out.println();
    }

    public synchronized void sendNodeState()
    {
        // send node current link state
        // vector to all neighbours
        // i.e. distancevector
        System.out.println("Sending node state");        
        int index = 0;
        for(int portNo : neighbourPorts)
        {
            LinkState state  = new LinkState(routerid, neighbourIds.get(index), distancevector);
            byte[] sendData = state.getBytes();
            DatagramPacket pkt = new DatagramPacket(sendData, sendData.length, IPAddress, portNo);
            try {
                UDPSocket.send(pkt);
            } catch (Exception e) {
                System.out.println("Broadcast error");
            }
            index++;
        }

        // reset timer
        timer.schedule(new SendStateTimer(this), 1000);
    }

    public int getNodeNotInN(int[] N)
    {
        // check that values 0-9 are
        for(int i=0;i<N.length;i++) {
            if(N[i] != i) {
                return i;
            }
        }
        return -1;
    }

    public int findMinNodeNotInN(int[] N)
    {
        int lowest = 1000;
        int lowestIndex = -1;
        for(int i=0;i<distancevector.length;i++)
        {
            if(distancevector[i]!= 0 && distancevector[i]<=lowest
                    && N[i]!=i)
            {
                lowest = distancevector[i];
                lowestIndex = i;
            }
        }
        if(lowestIndex == -1) {
            for(int val : distancevector)
            {
                System.out.println(val);
            }
        }
        else {
        }
        return lowestIndex;
    }

    public boolean allInfoReceived()
    {
        for(int[] vector : nodeData)
        {
            if(vector == null)
            {
                return false;
            }
        }
        return true;
    }

    public int getNodeDataSize()
    {
        int size = 0;
        for(int[] val:nodeData)
        {
            if(val != null)
            {
                size++;
            }
        }
        return size;
    }

    public synchronized void updateNodeRoute()
    {
        // if link state vectors of all
        // nodes received, calculate route
        // information, based on Dijstra
        // algorithm
        System.out.println("Node size = " + getNodeDataSize());
        System.out.println("Neighbours = " + noRouters);
        if(getNodeDataSize() == noRouters) {
            // calculate algorithm
            System.out.println("Algorithm starting...");

            // for all neighbours, cost = cost
            // for all other nodes, cost = infinity
            //

            // general process:
            // choose smallest out of distance vector
            // values
            // now update each distance vector value (
            // that is not in N) with the minimum of this
            // node to each destination, plus the cost
            // from root to it, or the original value

            // then start with N = u
            int[] N = new int[distancevector.length];
            N[routerid] = routerid;
            // loop while all nodes not in N
            while(getNodeNotInN(N) != -1) {
                // find node not in N such that d(node) is minimum
                // find minimum - not implemented
                int id = findMinNodeNotInN(N);
                int w = id; //distancevector[id];
                // add to N
                N[w] = w;
                // calculate new D(v) for
                // each router based on new w info
                for(int i=0;i<N.length;i++)
                {
                    // check if each router in N
                    if(true)//N[i]!=i)
                    {
                        // if not, compute either original
                        // value, or distance from root to
                        // w, then w to router
                        System.out.println("Comparing: "+distancevector[i]+" "+ distancevector[w] + nodeData.get(w)[i]+"N index="+i+" vect l="+N.length);
                        int newResult = Math.min(distancevector[i], distancevector[w] + nodeData.get(w)[i]);
                        if(newResult != distancevector[i])
                        {
                            // if the distance vector is getting
                            // updated from infinity the first time
                            previousvector[i] = w;
                        }
                        distancevector[i] = newResult;

                    }
                }
                // update D(v) for all v adjacent to node, and not in N
                //
            }
            // print results
            System.out.println("Routing Info");
            System.out.println("RouterID \t Distance \t Prev RouterID");
            int numNodes = noRouters;
            for(int i = 0; i < numNodes; i++)
            {
                System.out.println(i + "\t\t   "
                        + distancevector[i] +  "\t\t\t"
                        +  previousvector[i]);
            }
        } else {
            System.out.println("Not enough data yet");
        }

        // reset timer
        timer.schedule(new UpdateRouteTimer(this), 1000);
    }

    public synchronized void updateData(DatagramPacket packet)
    {
        // update link state data structure
        // updates nodeData with new info so
        // that updateNodeRoute can calcutate
        // the Dijstra algorithm
        byte[] data = packet.getData();
        LinkState state = new LinkState(data);

        // now insert into our ArrayList data
        // structure
        //System.out.println("Adding...");
        //System.out.println("ID: "+state.sourceId);
        nodeData.set(state.sourceId, state.getCost());
    }

    public void forwardData(DatagramPacket packet)
    {
        LinkState state = new LinkState(packet.getData());
        //System.out.println("Rec. orig. packet from "+state.sourceId);
        if(state.sourceId == routerid)
            return;
        int index = 0;
        // forward received packet to all neighbours 
        for(int portNo : neighbourPorts)
        {
            //System.out.println("Sending to: " + portNo);
            //state.destId = neighbourPorts.get(index);
            //System.out.println("Forwarding to: "+state.destId);
            byte[] sendData = state.getBytes();
            //System.out.println("Sending to port: "+portNo);
            DatagramPacket pkt = new DatagramPacket(sendData, sendData.length, IPAddress, portNo);
            try {
                UDPSocket.send(pkt);
            } catch (Exception e) {
                System.out.println("Forwarding broadcast error");
            }
            index++;
        }
    }

    /**
    *  Compute route information based on Dijkstra's algorithm and print the same
    * 
    */
    public void compute() {
        System.out.println("Computing...");
        // set up initial timer tasks
        timer.schedule(new SendStateTimer(this), 1000);
        timer.schedule(new UpdateRouteTimer(this), 1000);

        // create packet data size
        byte[] sendData = new byte[LinkState.MAX_SIZE];

        // run loop
        boolean runProgram = true;
        while(runProgram)
        {
            DatagramPacket rcvPkt = new DatagramPacket(sendData, sendData.length);
            // receive neighbour broadcasts
            try {
                UDPSocket.receive(rcvPkt);
            } catch (Exception e) {
                System.out.println("Socket receive error");
            }
            // when receive one, update info
            updateData(rcvPkt);
            // forward data to neighbours
            forwardData(rcvPkt);
        }
    }

    public byte[] readFile(String filePath)
    {
        // reads file by name from execution directory
        // and returns a byte array with its contents

        // create full path
        filePath = System.getProperty("user.dir") + "/" + filePath;
        
        // initialize values
        byte[] data = null;
        File file = null;

        // check path is correct
        try {
            file = new File(filePath);
        } catch (Exception e) {
            System.out.println("Invalid file path");
        }

        // try reading file into byte array
        String eachLine = null;
        try {
            data = new byte[(int) file.length()];
            FileInputStream fileStream = new FileInputStream(file);
            DataInputStream dataStream = new DataInputStream(fileStream);
            dataStream.read(data);
            fileStream.close();
            dataStream.close();
        } catch (Exception e) {
            // handle any exceptions
            System.out.println("File exception triggered");
            System.out.println("Message: " + e.getMessage());
        }
        // return file contents as byte array
        return data;
    }

    /* A simple test driver 
        
    */
    public static void main(String[] args) {
        
        String peerip = "127.0.0.1"; // all router programs running in the same machine for simplicity
        String configfile = "";
        int routerid = 999;
        int neighborupdate = 1000; // milli-seconds, update neighbor with link state vector every second
        int forwardtable = 10000; // milli-seconds, print route information every 10 seconds
        int port = -1; // router port number
    
        // check for command line arguments
        if (args.length == 3) {
            // either provide 3 parameters
            routerid = Integer.parseInt(args[0]);
            port = Integer.parseInt(args[1]);   
            configfile = args[2];
        }
        else {
            System.out.println("wrong number of arguments, try again.");
            System.out.println("usage: java Router routerid routerport configfile");
            System.exit(0);
        }

        
        Router router = new Router(peerip, routerid, port, configfile, neighborupdate, forwardtable);
        
        System.out.println("Router initialized..running");
        router.compute();
    }

}
