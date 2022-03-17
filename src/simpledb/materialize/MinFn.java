package simpledb.materialize;


import simpledb.query.*;

/**
 * The <i>max</i> aggregation function.
 * @author Edward Sciore
 */
public class MinFn implements AggregationFn {
   private String fldname;
   private Constant val;
   private boolean isDistinct;
   
   /**
    * Create a min aggregation function for the specified field.
    * @param fldname the name of the aggregated field
    */
   public MinFn(String fldname, boolean isDistinct) {
      this.fldname = fldname;
      this.isDistinct = isDistinct;
   }
   
   /**
    * Start a new maximum to be the 
    * field value in the current record.
    * @see simpledb.materialize.AggregationFn#processFirst(simpledb.query.Scan)
    */
   public void processFirst(Scan s) {
      val = s.getVal(fldname);
      
   }
   
   /**
    * Replace the current maximum by the field value
    * in the current record, if it is higher.
    * @see simpledb.materialize.AggregationFn#processNext(simpledb.query.Scan)
    */
   public void processNext(Scan s) {
      Constant newval = s.getVal(fldname);
      if (newval.compareTo(val) < 0)
         val = newval;
   }
   
   /**
    * Return the field's name, prepended by "maxof".
    * @see simpledb.materialize.AggregationFn#fieldName()
    */
   public String fieldName() {
	  if (this.isDistinct) {
		  return "min(DISTINCT " + fldname + ")";
	  }
      return "min(" + fldname + ")";
   }
   
   /**
    * Return the current maximum.
    * @see simpledb.materialize.AggregationFn#value()
    */
   public Constant value() {
      return val;
   }
   
   public String field() {
	   return this.fldname;
   }
}
