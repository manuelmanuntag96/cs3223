package simpledb.materialize;

import java.util.*;
import simpledb.tx.Transaction;
import simpledb.record.*;
import simpledb.plan.Plan;
import simpledb.query.*;

/**
 * The Plan class for the <i>distinct</i> operator.
 * @author Edward Sciore
 */
public class DistinctPlan implements Plan {
   private Transaction tx;
   private Plan p;
   private Schema sch;
   private RecordComparator comp;
   
   /**
    * Create a sort plan for the specified query.
    * @param p the plan for the underlying query
    * @param sortfields the fields to sort by
    * @param tx the calling transaction
    */
   public DistinctPlan(Transaction tx, Plan p, List<String> fields) {
      this.tx = tx;
      this.p = p;
      sch = p.schema();
      comp = new RecordComparator(fields);
   }
   
   /**
    * This method is where most of the action is.
    * Up to 2 sorted temporary tables are created,
    * and are passed into SortScan for final merging.
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      Scan src = p.open();
      Scan src2;
      List<TempTable> runs = splitIntoRuns(src);
      src.close();
      while (runs.size() > 1)
         runs = doADistinctIteration(runs);
      
      src2 = (runs.size() > 0) ? runs.get(0).open() : src;
      return new DistinctScan(src2);
   }
   
   /**
    * Return the number of blocks in the sorted table,
    * which is the same as it would be in a
    * materialized table.
    * It does <i>not</i> include the one-time cost
    * of materializing and sorting the records.
    * @see simpledb.plan.Plan#blocksAccessed()
    */
   public int blocksAccessed() {
      // does not include the one-time cost of sorting
      Plan mp = new MaterializePlan(tx, p); // not opened; just for analysis
      return mp.blocksAccessed();
   }
   
   /**
    * Return the number of records in the sorted table,
    * which is the same as in the underlying query.
    * @see simpledb.plan.Plan#recordsOutput()
    */
   public int recordsOutput() {
      return p.recordsOutput();
   }
   
   /**
    * Return the number of distinct field values in
    * the sorted table, which is the same as in
    * the underlying query.
    * @see simpledb.plan.Plan#distinctValues(java.lang.String)
    */
   public int distinctValues(String fldname) {
      return p.distinctValues(fldname);
   }
   
   /**
    * Return the schema of the sorted table, which
    * is the same as in the underlying query.
    * @see simpledb.plan.Plan#schema()
    */
   public Schema schema() {
      return sch;
   }
   
   private List<TempTable> splitIntoRuns(Scan src) {
      List<TempTable> temps = new ArrayList<>();
      src.beforeFirst();
      if (!src.next())
         return temps;
      TempTable currenttemp = new TempTable(tx, sch);
      temps.add(currenttemp);
      UpdateScan currentscan = currenttemp.open();
      while (copy(src, currentscan))
         if (comp.compare(src, currentscan) < 0) {
         // start a new run
         currentscan.close();
         currenttemp = new TempTable(tx, sch);
         temps.add(currenttemp);
         currentscan = (UpdateScan) currenttemp.open();
      }
      currentscan.close();
      return temps;
   }
   
   private List<TempTable> doADistinctIteration(List<TempTable> runs) {
      List<TempTable> result = new ArrayList<>();
      while (runs.size() > 1) {
         TempTable p1 = runs.remove(0);
         TempTable p2 = runs.remove(0);
         result.add(mergeTwoRuns(p1, p2));
      }
      if (runs.size() == 1)
         result.add(runs.get(0));
      return result;
   }
   
   private TempTable mergeTwoRuns(TempTable p1, TempTable p2) {
      Scan src1 = p1.open();
      Scan src2 = p2.open();
      TempTable result = new TempTable(tx, sch);
      UpdateScan dest = result.open();
      
      boolean hasmore1 = src1.next();
      boolean hasmore2 = src2.next();
      
      // get a record into dest first if not ltr error will be thrown
      if (hasmore1 && hasmore2) {
    	  if (comp.compare(src1, src2) < 0) {
    		  hasmore1 = copy(src1, dest);
    	  } else {
    		  hasmore2 = copy(src2, dest);
    	  }
      }
      
      while (hasmore1 && hasmore2) {
    	  // src1 < src2
    	  if (comp.compare(src1, src2) < 0) {
    		  // add src1 to dest if no duplicate
    		  hasmore1 = !same(src1, dest) ? copy(src1, dest) : src1.next();
		  
		  // src1 >= src2, no duplicate
    	  } else if (!same(src2, dest)) {
    		  hasmore2 = copy(src2, dest);
    	  } else {
    		  hasmore2 = src2.next();
    	  }
      }
      
      while (hasmore1) {
    	  hasmore1 = !same(src1, dest) ? copy(src1, dest) : src1.next();
      }
      
      while (hasmore2) {
    	  hasmore2 = !same(src2, dest) ? copy(src2, dest) : src2.next();
      }
      
      
      src1.close();
      src2.close();
      dest.close();
      return result;
   }
   
   // See if two records are the same
   private boolean same(Scan src1, Scan src2) {
	   if (src1 == null || src2 == null)
		   return false;
	   return comp.compare(src1, src2) == 0;
   }
   
   private boolean copy(Scan src, UpdateScan dest) {
      dest.insert();
      for (String fldname : sch.fields())
         dest.setVal(fldname, src.getVal(fldname));
      return src.next();
   }
}
