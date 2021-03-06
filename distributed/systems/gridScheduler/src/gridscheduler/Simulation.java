package gridscheduler;

import gridscheduler.gui.ClusterStatusPanel;
import gridscheduler.gui.GridSchedulerPanel;
import gridscheduler.model.Cluster;
import gridscheduler.model.Job;
import gridscheduler.model.Supervisor;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.concurrent.ThreadLocalRandom;


/**
 *
 * The Simulation class is an example of a grid computation scenario. Every 100 milliseconds 
 * a new job is added to first cluster. As this cluster is swarmed with jobs, it offloads
 * some of them to the grid scheduler, which in turn passes them to the other clusters.
 * 
 * @author Niels Brouwers, Boaz Pat-El
 */
public class Simulation implements Runnable,KeyListener {
	// Number of clusters in the simulation
	private final static int nrClusters = 8;

	// Number of nodes per cluster in the simulation
	private final static int nrNodes = 1000;

	// Simulation components
	Cluster clusters[];

	GridSchedulerPanel gridSchedulerPanel;

    private Supervisor supervisor = null;

	private static long jobCreationRatio = 50L;
	private static long jobDuration = 40000L;//8000L

    private boolean gsNodeFaultToggle = false;

	private final static Logger logger = Logger.getLogger(Simulation.class.getName());
	private static DecimalFormat df2 = new DecimalFormat(".##");

	public long jobId;
	/**
	 * Constructs a new simulation object. Study this code to see how to set up your own
	 * simulation.
	 */
	public Simulation() throws IOException {

		BasicConfigurator.configure();

		// TODO if something goes wrong recheck this logic
		//GridSchedulerNode scheduler;

		jobId = 0;

		// Setup the model. Create a grid scheduler and a set of clusters.
		//scheduler = new GridSchedulerNode("scheduler1");
		supervisor = new Supervisor("Supervisor",4,false); // TODO change this in order to have variable number of grid scheduler nodes

		// Create a new gridscheduler panel so we can monitor our components
		//gridSchedulerPanel = new GridSchedulerPanel(scheduler);
		gridSchedulerPanel = new GridSchedulerPanel(supervisor);
		gridSchedulerPanel.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		//logger.info("Simulation started.");

		// Create the clusters and nods
		clusters = new Cluster[nrClusters];
		for (int i = 0; i < nrClusters; i++) {
			clusters[i] = new Cluster("cluster" + i, supervisor, nrNodes);

			// Now create a cluster status panel for each cluster inside this gridscheduler
			ClusterStatusPanel clusterReporter = new ClusterStatusPanel(clusters[i]);
			gridSchedulerPanel.addStatusPanel(clusterReporter);
		}

		// Open the gridscheduler panel
		gridSchedulerPanel.start();

		// Run the simulation
		Thread runThread = new Thread(this);
		runThread.run(); // This method only returns after the simulation has ended

		// Now perform the cleanup

		// Stop clusters
		for (Cluster cluster : clusters)
			cluster.stopPollThread();

		// Stop grid scheduler
		supervisor.stopPollThread();
	}

	/**
	 * The main run thread of the simulation. You can tweak or change this code to produce
	 * different simulation scenarios.
	 */
	public void run() {


		gridSchedulerPanel.addKeyListener(this);

        int highLoadTargetCluster = ThreadLocalRandom.current().nextInt(0, nrClusters);
        int lowLoadTargetCluster = ThreadLocalRandom.current().nextInt(0, nrClusters);
		// Do not stop the simulation as long as the gridscheduler panel remains open
		long jobLimit = 10000;
		while (gridSchedulerPanel.isVisible() && jobId < jobLimit) {

			System.out.println("Job id: " + jobId);
			// Uncomment one at a time in order to simulate different behaviours
            //idealLoad(jobId++);
            //stressTest(jobId++, 5);
			//evenLoad(jobId++); // randomly distributes jobs to cluster (nearly uniform distribution)
			unEvenLoad(jobId++, highLoadTargetCluster, lowLoadTargetCluster,5); //TODO make the ratio parameterized (extreme high load)
			//loadSameJobOnMultipleClusters(jobId,3); // load arg[2] clusters with the same job (almost) simultaneously

			try {
				// Sleep a while before creating a new job
				Thread.sleep(jobCreationRatio);
			} catch (InterruptedException e) {
				assert (false) : "Simulation runtread was interrupted";
			}

		}

	}

	public void idealLoad(long jobId){
        // Add a new job to the system that take up random time

        for(Cluster cluster:clusters) {
            Job job = new Job(jobDuration, jobId++);
            cluster.getResourceManager().addJob(job);
        }
    }

    public void stressTest(long jobId, int ratio){
        for (int i = 0; i < ratio; i++){
            Job job = new Job(jobDuration, jobId++);
            clusters[0].getResourceManager().addJob(job);
        }
        Job job = new Job(jobDuration, jobId);
        clusters[clusters.length - 1].getResourceManager().addJob(job);
    }

	public void evenLoad(long jobId){
		// Add a new job to the system that take up random time
		Job job = new Job(jobDuration + (int) (Math.random() * 5000), jobId);
		clusters[ThreadLocalRandom.current().nextInt(0, nrClusters)].getResourceManager().addJob(job);
	}

	public void unEvenLoad(long jobId, int highLoadTargetCluster, int lowLoadTargetCluster, int ratio){

		for (int i = 0; i < ratio; i++) {
			// Add a new job to the system that take up random time
			Job job = new Job(jobDuration + (int) (Math.random() * 5000), jobId++);
			clusters[highLoadTargetCluster].getResourceManager().addJob(job);
		}
		Job job = new Job(jobDuration + (int) (Math.random() * 5000), jobId++);
		clusters[lowLoadTargetCluster].getResourceManager().addJob(job);
	}

	public void loadSameJobOnMultipleClusters(long jobId, int noClusters){
		// Add a new job to the system that take up random time
		Job job = new Job(jobDuration + (int) (Math.random() * 5000), jobId++);
		for(int i = 0; i < noClusters; i++) {
			clusters[ThreadLocalRandom.current().nextInt(0, nrClusters)].getResourceManager().addJob(job);
		}
	}

	public long getJobId(){
		return this.jobId;
	}

	@Override
	public void keyTyped(KeyEvent e) {

	}

	@Override
	public void keyPressed(KeyEvent e) {

	}

	@Override
	public void keyReleased(KeyEvent e) {
		// on UP key pressed produce jobs faster
		if (e.getKeyCode() == KeyEvent.VK_UP ) {
			//precondition: the job creation ratio can't reach this threshold
			if(jobCreationRatio > 50) {
				jobCreationRatio -= 50;
			}
			logger.warn("Job creation ratio INCREASED to " + df2.format(1000/(double)jobCreationRatio) + " jobs/sec.");
		}
		// on DOWN key pressed produce jobs slower
		if (e.getKeyCode() == KeyEvent.VK_DOWN ) {
			jobCreationRatio += 50;
			logger.warn("Job creation ratio DECREASED to " + df2.format(1000/(double)jobCreationRatio) + " jobs/sec.");
		}
		// on LEFT key pressed decrease the job duration
		if (e.getKeyCode() == KeyEvent.VK_LEFT ) {

			//precondition: the job duration can't be less than 0,1 sec
			if(jobDuration > 100) {
				jobDuration -= 100;
			}
			logger.warn("Job duration DECREASED to " + df2.format((double) jobDuration/1000) + " sec.");
		}
		// on RIGHT key pressed increase the job duration
		if (e.getKeyCode() == KeyEvent.VK_RIGHT ) {
			jobDuration += 100;
			logger.warn("Job duration INCREASED to " + df2.format((double) jobDuration/1000)  + " sec.");
		}

		if (e.getKeyCode() == KeyEvent.VK_G) {

		    if(!gsNodeFaultToggle) {

		        supervisor.injectGSnodeFault(!gsNodeFaultToggle);
                gsNodeFaultToggle = true;
                logger.fatal("A GS node was forced to go DOWN");
            }else{
                supervisor.injectGSnodeFault(gsNodeFaultToggle);
		        gsNodeFaultToggle = false;
                logger.fatal("A GS node was forced to go UP");
            }

        }
	}

	/**
	 * Application entry point.
	 *
	 * @param args application parameters
	 */
	public static void main(String[] args) throws IOException {
		// Create and run the simulation
		new Simulation();
	}

}

