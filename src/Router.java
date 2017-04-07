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

    // class variables
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
    public Router(String peerip, int routerid, int port, String configfile, int neighborupdate, int routeupdate) {
        // save data
        this.peerip = peerip;
        this.port = port;
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

        // create timer object
        timer = new Timer();

        // initialize data structures with
        // default information
        initializeDataStructures(configfile);
    }

    public void initializeDataStructures(String path)
    {
        // sets up the initial data structure
        // conditions based on input parameters
        // and the config file contents
        
        // read config file
        byte[] fileData = readFile(path);
        String fileString = new String(fileData);
        String fileLines[] = fileString.split("\n");

        // extract total number of network routers
        String[] firstLine = fileLines[0].split(" ");
        noRouters = Integer.parseInt(firstLine[0]);

        // initialize link state vectors
        distancevector = new int[noRouters];
        previousvector = new int[noRouters];

        // create data structure, updated
        // when neighbour broadcast is
        // received
        nodeData = new LinkedList<int[]>();
        neighbourIds = new ArrayList<Integer>();
        neighbourPorts = new LinkedList<Integer>();
        
        // initialize all node link states
        // to null
        for(int i=0;i<noRouters;i++)
        {
            nodeData.add(null);
        }

        // initialize vector with infinity values
        // and previous vector values with current
        // router id
        for(int i=0;i<distancevector.length;i++) {
            distancevector[i] = 999;
            previousvector[i] = routerid;
        }

        // initialize self cost as zero
        distancevector[routerid] = 0;

        // now initialize with neighbour values
        // extracted from the config file
        for(int i=1;i<fileLines.length;i++)
        {
            String[] currentLine = fileLines[i].split(" ");
            int routerId = Integer.parseInt(currentLine[1]);
            // record all neighbour ids
            neighbourIds.add(routerId);
            // record all neighbour ports
            neighbourPorts.add(Integer.parseInt(currentLine[3]));
            int idCost = Integer.parseInt(currentLine[2]);
            // update vector with cost
            distancevector[routerId] = idCost;
        }

        // set current router link state
        // to current link state
        nodeData.set(routerid, distancevector);
    }

    public synchronized void sendSocketData(DatagramPacket packet, String errorMessage)
    {
        // a helper method that provides
        // thread safea access to the socket
        // send method
        try {
            UDPSocket.send(packet);
        } catch (Exception e) {
            System.out.println(errorMessage);
        }
    }

    public synchronized void sendNodeState()
    {
        // send node current link state
        // vector to all neighbours
        int index = 0;
        for(int portNo : neighbourPorts)
        {
            LinkState state  = new LinkState(routerid, neighbourIds.get(index), distancevector);
            byte[] sendData = state.getBytes();
            DatagramPacket pkt = new DatagramPacket(sendData, sendData.length, IPAddress, portNo);
            sendSocketData(pkt, "Broadcast error");
            index++;
        }

        // reset timer
        timer.schedule(new SendStateTimer(this), 1000);
    }

    public synchronized void updateNodeRoute()
    {
        // if link state vectors of all
        // nodes received, calculate route
        // information, based on Dijstra
        // algorithm
        if(getNodeDataSize() == noRouters) {
            // Dijkstra algorithm
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
            while(findMinNodeNotInN(N) != -1) {
                // find node w not in N such that d(node) is minimum
                int w = findMinNodeNotInN(N);
                // add to N
                N[w] = w;
                // calculate new D(v) for
                // each router based on new w info
                for(int i=0;i<N.length;i++)
                {
                    // compute min of original
                    // value, or distance from root to
                    // w, then w to router
                    int newResult = Math.min(distancevector[i], distancevector[w] + nodeData.get(w)[i]);
                    // if a new value, update previous router value
                    if(newResult != distancevector[i])
                        previousvector[i] = w;
                    // update distance vector with new result
                    distancevector[i] = newResult;
                    // update current node link state
                    // with new vector
                    nodeData.set(routerid, distancevector);
                }
            }

            // now that the distancevector has been
            // updated, print results
            System.out.println("\nRouting Info for Router ID: "+routerid);
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

    public int findMinNodeNotInN(int[] N)
    {
        // returns the minimum node value
        // not in the provided array
        // if no values not in N, return -1
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
        return lowestIndex;
    }

    public int getNodeDataSize()
    {
        // returns the "true" size
        // of node data structure
        int size = 0;
        for(int[] val:nodeData)
        {
            if(val != null)
                size++;
        }
        return size;
    }


    public void updateData(DatagramPacket packet)
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
        // Broadcast Algorithm
        // All received packets are forwarded
        // to all neighbour ports, unless
        // originally sent my current router
        // Messages will 'live forever'
        
        // convert received data into link state
        LinkState state = new LinkState(packet.getData());

        // if packet originally from current
        // router, do not forward again
        if(state.sourceId == routerid)
            return;

        // forward received packet to all neighbours 
        for(int portNo : neighbourPorts)
        {
            byte[] sendData = state.getBytes();
            DatagramPacket pkt = new DatagramPacket(sendData, sendData.length, IPAddress, portNo);
            sendSocketData(pkt, "Forwarding broadcast error");
        }
    }

    /**
    *  Compute route information based on Dijkstra's algorithm and print the same
    * 
    */
    public void compute() {
        // Main Program Logic
        
        // set up initial timer tasks
        timer.schedule(new SendStateTimer(this), 1000);
        timer.schedule(new UpdateRouteTimer(this), 1000);

        // create packet data size
        byte[] sendData = new byte[LinkState.MAX_SIZE];

        // run main program loop
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
            // when receive packet, update data
            // structure info
            updateData(rcvPkt);
            // then forward data to all neighbours
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
