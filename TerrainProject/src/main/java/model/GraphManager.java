package model;

import com.hoten.delaunay.voronoi.VoronoiGraph;
import com.hoten.delaunay.voronoi.nodename.as3delaunay.Voronoi;

import java.util.List;
import java.util.Random;

import controller.generation.TerrainCircle;

/**
 * Manages the graph models and stores their initial values
 * Created by P on 04.12.2015.
 */
public class GraphManager {

    final int bounds = 1000;
    final int numSites;
    final int numLloydRelaxations = 2;
    private VoronoiGraph graph;
    private Voronoi voronoi;

    public GraphManager(Random r, long seed, int resolution) {
       this(r,seed,resolution, VoronoiGraph.Generation_Type.RANDOM,null);
    }

    public GraphManager(Random r, long seed, int resolution, VoronoiGraph.Generation_Type generation_type, List<TerrainCircle> circles) {
        this.numSites = resolution;

        voronoi = new Voronoi(numSites, bounds, bounds, r, null);
        graph = new DefaultVoronoiGraph(voronoi, numLloydRelaxations, r, generation_type, circles);

        // Save the Map to a file
        System.out.printf("seed-%s sites-%d lloyds-%d\n", seed, numSites, numLloydRelaxations);
    }

    public VoronoiGraph getGraph() {
        return graph;
    }

    public Voronoi getVoronoi() {
        return voronoi;
    }

}
