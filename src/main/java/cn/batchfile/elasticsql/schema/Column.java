package cn.batchfile.elasticsql.schema;

public class Column {

	public String name;
	public Type type;
	//public boolean allow_null;
	public Object null_value;
	//public String extra;
	public Index index = Index.analyzed;
	public String analyzer;
	public String index_analyzer;
	public String search_analyzer;
	public boolean store = true;

}
