package src;
import java.net.*;
import java.io.*;
import java.util.*;

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
     	* @param peerip		IP address of other routers (we assume that all routers are running in the same machine)
     	* @param routerid	Router ID
     	* @param port		Router UDP port number
     	* @param configfile	Configuration file name
	    * @param neighborupdate	link state update interval - used to update router's link state vector to neighboring nodes
        * @param routeupdate 	Route update interval - used to update route information using Dijkstra's algorithm
 
     */

    // variables
    private Timer timer;
    private DatagramSocket UDPSocket;
    private String peerip;
    private int port;
    private InetAddress IPAddress;
    private int[] distancevector;
    private int routerid;
    private int noNeighbours;
    private ArrayList<int[]> nodeData;
    private LinkedList<Integer> neighbourPorts;
    private ArrayList<Integer> neighbourIds;

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


        // create data structure, updated
        // when neighbour broadcast is
        // received
        nodeData = new ArrayList<int[]>();
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
        int noRouters = Integer.parseInt(fileLines[0]);
        noNeighbours = noRouters;

        distancevector = new int[noRouters];

        // initialize vector with infinity values
        for(int i=0;i<distancevector.length;i++) {
            distancevector[i] = 999;
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
        
        // print initial vector state
        for(int val : distancevector)
        {
            System.out.print(val + " ");
        }
        System.out.println();
    }

    public void sendNoteState()
    {
        // send node current link state
        // vector to all neighbours
        // i.e. distancevector
        
        int index = 0;
        for(int portNo : neighbourPorts)
        {
            LinkState state  = new LinkState(0, neighbourIds.get(index), distancevector);
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

    public void updateNodeRoute()
    {
        // if link state vectors of all
        // nodes received, calculate route
        // information, based on Dijstra
        // algorithm
        System.out.println("Currently no algorithm implemented");
        // print results
        System.out.println("Routing Info");
        System.out.println("RouterID \t Distance \t Prev RouterID");
        int numNodes = noNeighbours;
        int[] prev = new int[15];
        for(int i = 0; i < numNodes; i++)
        {
            System.out.println(i + "\t\t   " + distancevector[i] +  "\t\t\t" +  prev[i]);
        }

        // reset timer
        timer.schedule(new UpdateRouteTimer(this), 1000);
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
        nodeData.add(state.sourceId, state.getCost());
    }

    public void forwardData(DatagramPacket packet)
    {
        // forward received packet to all neighbours 
        for(int portNo : neighbourPorts)
        {
            try {
                UDPSocket.send(packet);
            } catch (Exception e) {
                System.out.println("Forwarding broadcast error");
            }
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
            DatagramPacket rcvPkt = new DatagramPacket(sendData, sendData.length, IPAddress, port);
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
            // broadcast new node status
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
