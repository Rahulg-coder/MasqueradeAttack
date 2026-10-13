package com.canids.research.road.ml;
import java.util.*;
/** Custom binary Random Forest from paper Section IV-E.
 *
 * <p>Flow per tree: bootstrap training rows -> sample sqrt(feature count) candidate
 * features at each node -> choose the split with greatest Gini reduction -> recurse
 * until a stopping rule -> store the leaf attack probability. Prediction averages
 * the probabilities across trees.
 */
public final class RandomForestClassifier {
    private final int trees, maxDepth, minSplit, minLeaf;
    private final long seed;
    private final List<Node> forest=new ArrayList<>();
    private int dimensions;
    public RandomForestClassifier(int trees, int maxDepth, int minSplit, int minLeaf, long seed) {
        if(trees<1||maxDepth<1||minSplit<2||minLeaf<1)throw new IllegalArgumentException("Invalid forest parameters");
        this.trees=trees;
        this.maxDepth=maxDepth;
        this.minSplit=minSplit;
        this.minLeaf=minLeaf;
        this.seed=seed;
    }
    /** Fits bootstrap trees using randomized Gini-impurity splits. */
    public void fit(double[][] x, int[] y) {
        if(x.length==0||x.length!=y.length)throw new IllegalArgumentException("Empty/mismatched data");
        dimensions=x[0].length;
        forest.clear();
        Random r=new Random(seed);
        for(int t=0; t<trees; t++) {
            int[] b=new int[x.length];
            for(int i=0; i<b.length; i++)b[i]=r.nextInt(x.length);
            forest.add(build(x, y, b, 0, new Random(r.nextLong())));
        }
    }
    /** Returns the mean forest probability for the attack class. */
    public double predictProbability(double[] x) {
        if(forest.isEmpty())throw new IllegalStateException("Forest not fitted");
        double p=0;
        for(Node n:forest)p+=predict(n, x);
        return p/forest.size();
    }
    private Node build(double[][] x, int[] y, int[] rows, int depth, Random r) {
        int pos=0;
        for(int i:rows)if(y[i]==1)pos++;
        double prob=(double)pos/rows.length;
        Node leaf=new Node(prob);
        if(depth>=maxDepth||rows.length<minSplit||pos==0||pos==rows.length)return leaf;
        int[] fs=new int[dimensions];
        for(int i=0; i<dimensions; i++)fs[i]=i;
        for(int i=dimensions-1; i>0; i--) {
            int j=r.nextInt(i+1), z=fs[i];
            fs[i]=fs[j];
            fs[j]=z;
        }
        Split best=null;
        for(int j=0; j<Math.max(1, (int)Math.sqrt(dimensions)); j++) {
            Split s=split(x, y, rows, fs[j]);
            if(s!=null&&(best==null||s.gain>best.gain))best=s;
        }
        if(best==null||best.gain<=1e-12)return leaf;
        int n=0;
        for(int i:rows)if(x[i][best.feature]<=best.threshold)n++;
        if(n<minLeaf||rows.length-n<minLeaf)return leaf;
        int[] a=new int[n], b=new int[rows.length-n];
        int l=0, h=0;
        for(int i:rows)if(x[i][best.feature]<=best.threshold)a[l++]=i;
        else b[h++]=i;
        Node node=new Node(prob);
        node.feature=best.feature;
        node.threshold=best.threshold;
        node.left=build(x, y, a, depth+1, r);
        node.right=build(x, y, b, depth+1, r);
        return node;
    }
    /** Finds the best sampled threshold for one feature using Gini reduction. */
    private Split split(double[][] x, int[] y, int[] rows, int f) {
        Integer[] s=Arrays.stream(rows).boxed().toArray(Integer[]::new);
        Arrays.sort(s, Comparator.comparingDouble(i->x[i][f]));
        int total=0;
        for(int i:rows)if(y[i]==1)total++;
        int left=0, step=Math.max(1, s.length/32);
        double parent=gini(total, s.length), gain=0, threshold=0;
        for(int i=1; i<s.length; i++) {
            if(y[s[i-1]]==1)left++;
            if(i<minLeaf||s.length-i<minLeaf)continue;
            double a=x[s[i-1]][f], b=x[s[i]][f];
            if(!(a<b)||(i%step!=0&&i!=s.length-1))continue;
            double g=parent-((double)i/s.length*gini(left, i)+(double)(s.length-i)/s.length*gini(total-left, s.length-i));
            if(g>gain) {
                gain=g;
                threshold=a/2+b/2;
            }
        }
        return gain>0?new Split(f, threshold, gain):null;
    }
    private static double gini(int p, int n) {
        if(n==0)return 0;
        double z=(double)p/n;
        return 2*z*(1-z);
    }
    private static double predict(Node n, double[] x) {
        while(n.left!=null)n=x[n.feature]<=n.threshold?n.left:n.right;
        return n.prob;
    }
    private static final class Node {
        final double prob;
        int feature=-1;
        double threshold;
        Node left, right;
        Node(double p) {
            prob=p;
        }
    }
    private record Split(int feature, double threshold, double gain) {
    }
}
