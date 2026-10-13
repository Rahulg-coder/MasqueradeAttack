package com.canids.research.road.graph;
import com.canids.research.road.feature.WindowSample;
import java.util.*;
/** Node2Vec stage from paper Section IV-D.
 *
 * <p>Flow: aggregate training-fold transitions -> generate weighted p/q-biased
 * second-order walks -> train skip-gram vectors with negative sampling -> mean-pool
 * known node vectors for each window.
 *
 * <p>The paper specifies 64 dimensions, 100 walks per node, length 15, p=1.5, q=0.5.
 * This implementation fits one shared encoder on training-fold graphs instead of
 * embedding each window separately. Context radius, negative count, epochs, and
 * learning rate are implementation choices because the paper does not specify them.
 */
public final class Node2VecEncoder {
    private final int dimensions, walksPerNode, walkLength, epochs;
    private final double p, q;
    private final long seed;
    private Map<String, double[]> vectors=Map.of();
    public Node2VecEncoder(int dimensions, int walksPerNode, int walkLength, double p, double q, int epochs, long seed) {
        if(dimensions<1||walksPerNode<1||walkLength<1||p<=0||q<=0||epochs<1)throw new IllegalArgumentException("Invalid Node2Vec parameters");
        this.dimensions=dimensions;
        this.walksPerNode=walksPerNode;
        this.walkLength=walkLength;
        this.p=p;
        this.q=q;
        this.epochs=epochs;
        this.seed=seed;
    }
    /** Learns the shared node-vector table from the supplied training windows only. */
    public void fit(List<WindowSample> samples) {
        Map<String, Map<String, Double>> graph=new TreeMap<>();
        for(WindowSample s:samples) {
            WindowGraph g=s.graph();
            for(String n:g.nodes())graph.computeIfAbsent(n, k->new TreeMap<>());
            for(String u:g.nodes())for(var e:g.outgoing(u).entrySet()) {
                graph.computeIfAbsent(u, k->new TreeMap<>()).merge(e.getKey(), e.getValue(), Double::sum);
                graph.computeIfAbsent(e.getKey(), k->new TreeMap<>());
            }
        }
        if(graph.isEmpty()) {
            vectors=Map.of();
            return;
        }
        Random rnd=new Random(seed);
        List<String> vocab=List.copyOf(graph.keySet());
        List<List<String>> corpus=new ArrayList<>();
        Map<String, Integer> freq=new HashMap<>();
        for(String n:vocab)for(int w=0; w<walksPerNode; w++) {
            List<String> walk=walk(n, graph, rnd);
            corpus.add(walk);
            for(String t:walk)freq.merge(t, 1, Integer::sum);
        }
        Map<String, Integer> ids=new HashMap<>();
        for(int i=0; i<vocab.size(); i++)ids.put(vocab.get(i), i);
        double[][] in=new double[vocab.size()][dimensions], out=new double[vocab.size()][dimensions];
        for(double[] v:in)for(int d=0; d<dimensions; d++)v[d]=(rnd.nextDouble()-.5)/dimensions;
        double[] cdf=new double[vocab.size()];
        double total=0;
        for(int i=0; i<vocab.size(); i++) {
            total+=Math.pow(freq.getOrDefault(vocab.get(i), 1), .75);
            cdf[i]=total;
        }
        for(int epoch=0; epoch<epochs; epoch++) {
            Collections.shuffle(corpus, rnd);
            for(List<String> walk:corpus)for(int i=0; i<walk.size(); i++) {
                int lo=Math.max(0, i-5), hi=Math.min(walk.size()-1, i+5), source=ids.get(walk.get(i));
                for(int j=lo; j<=hi; j++)if(j!=i) {
                    int target=ids.get(walk.get(j));
                    update(in[source], out[target], 1);
                    for(int n=0; n<5; n++) {
                        int neg=sample(cdf, total, rnd);
                        if(neg!=target)update(in[source], out[neg], 0);
                    }
                }
            }
        }
        Map<String, double[]> learned=new HashMap<>();
        for(int i=0; i<vocab.size(); i++)learned.put(vocab.get(i), in[i]);
        vectors=Map.copyOf(learned);
    }
    /** Mean-pools learned vectors for the CAN IDs in one window graph. */
    public double[] encode(WindowGraph graph) {
        double[] r=new double[dimensions];
        int count=0;
        for(String n:graph.nodes()) {
            double[] v=vectors.get(n);
            if(v!=null) {
                for(int d=0; d<dimensions; d++)r[d]+=v[d];
                count++;
            }
        }
        if(count>0)for(int d=0; d<dimensions; d++)r[d]/=count;
        return r;
    }
    /** Samples a weighted second-order walk, applying p to returns and q to outward moves. */
    private List<String> walk(String start, Map<String, Map<String, Double>> graph, Random rnd) {
        List<String> r=new ArrayList<>();
        r.add(start);
        String prev=null, cur=start;
        while(r.size()<walkLength) {
            Map<String, Double> nexts=graph.getOrDefault(cur, Map.of());
            if(nexts.isEmpty())break;
            List<String> ns=new ArrayList<>(nexts.keySet());
            double[] w=new double[ns.size()];
            double sum=0;
            for(int i=0; i<ns.size(); i++) {
                String n=ns.get(i);
                double bias=1;
                if(prev!=null) {
                    if(n.equals(prev))bias=1/p;
                    else if(!graph.getOrDefault(prev, Map.of()).containsKey(n))bias=1/q;
                }
                w[i]=nexts.get(n)*bias;
                sum+=w[i];
            }
            double pick=rnd.nextDouble()*sum;
            int chosen=ns.size()-1;
            for(int i=0; i<ns.size(); i++) {
                pick-=w[i];
                if(pick<=0) {
                    chosen=i;
                    break;
                }
            }
            prev=cur;
            cur=ns.get(chosen);
            r.add(cur);
        }
        return r;
    }
    private static int sample(double[] cdf, double total, Random rnd) {
        double x=rnd.nextDouble()*total;
        int lo=0, hi=cdf.length-1;
        while(lo<hi) {
            int m=(lo+hi)>>>1;
            if(x<=cdf[m])hi=m;
            else lo=m+1;
        }
        return lo;
    }
    /** Applies one logistic skip-gram update to an input/output vector pair. */
    private static void update(double[] a, double[] b, int y) {
        double dot=0;
        for(int i=0; i<a.length; i++)dot+=a[i]*b[i];
        double prob=dot>12?.999994:dot< -12?.000006:1/(1+Math.exp(-dot));
        double g=(y-prob)*.025;
        for(int i=0; i<a.length; i++) {
            double x=a[i], z=b[i];
            a[i]=x+g*z;
            b[i]=z+g*x;
        }
    }
}
