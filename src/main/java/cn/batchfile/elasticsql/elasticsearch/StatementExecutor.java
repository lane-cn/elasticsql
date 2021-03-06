package cn.batchfile.elasticsql.elasticsearch;

import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.nlpcn.es4sql.SearchDao;
import org.nlpcn.es4sql.exception.SqlParseException;

import com.github.mpjct.jmpjct.mysql.proto.Column;
import com.github.mpjct.jmpjct.mysql.proto.ResultSet;
import com.github.mpjct.jmpjct.mysql.proto.Row;

import cn.batchfile.elasticsql.exceptions.ExecuteException;
import cn.batchfile.elasticsql.util.Rest;

public class StatementExecutor {
	
	private static final Logger logger = Logger.getLogger("StatementExecutor");
	private SearchDao searchDao;

	public void connect(String httpAddress, String transportAddress) {
		String cluster_name = getClusterName(httpAddress.split(","));
		
		Settings settings = ImmutableSettings.settingsBuilder()
				.put("client.transport.sniff", true)
				.put("cluster.name", cluster_name).build();
		
		TransportClient client = new TransportClient(settings);
		for (String host : transportAddress.split(",")) {
			if (StringUtils.isBlank(host)) {
				continue;
			}
			
			String ip = StringUtils.substringBefore(host, ":");
			String port = StringUtils.substringAfter(host, ":");
			client.addTransportAddress(new InetSocketTransportAddress(ip, Integer.valueOf(port)));
		}
		
		searchDao = new SearchDao(client);
	}
	
	public Result execute(String sql) {
		Result ret = new Result();
		logger.debug("-> " + sql);
		
		if (StringUtils.containsIgnoreCase(sql, "select @@version_comment")) {
			ResultSet rs = new ResultSet();
			
			Column col = new Column("@@version_comment");
			rs.addColumn(col);
			
			Row row = new Row();
			row.addData("Source distribution");
			rs.addRow(row); 
			
			ret.resultSet = rs;
		} else {
			try {
				SearchResponse response = explain(sql).get();
				ResultHandler handler = ResultHandlerFactory.create(response);
				
				ResultSet rs = new ResultSet();
				List<String> heads = handler.getHead();
				for (String head : heads) {
					rs.addColumn(new Column(head));
				}
				
				List<Map<String, Object>> body = handler.getBody();
				for (Map<String, Object> map : body) {
					Row row = new Row();
					for (Entry<String, Object> entry : map.entrySet()) {
						Object value = entry.getValue();
						if (value == null) {
							row.addData(StringUtils.EMPTY);
						} else if (value instanceof String) {
							row.addData((String)value);
						} else if (value instanceof Integer) { 
							row.addData(String.format("%d", value));
						} else if (value instanceof Boolean) {
							row.addData((Boolean)value);
						} else if (value instanceof Float) {
							row.addData(String.format("%f", value));
						} else if (value instanceof Double) {
							row.addData(String.format("%f", value));
						} else if (value instanceof Long) {
							row.addData((Long)value);
						} else {
							row.addData(value.toString());
						}
					}
					rs.addRow(row);
				}
				ret.resultSet = rs;
			} catch (SQLFeatureNotSupportedException e) {
				throw new ExecuteException(1011, StringUtils.EMPTY, e.getMessage(), e);
			} catch (SqlParseException e) {
				throw new ExecuteException(1012, StringUtils.EMPTY, e.getMessage(), e);
			} catch (Exception e) {
				throw new ExecuteException(1050, StringUtils.EMPTY, e.getMessage(), e);
			}
		}
		return ret;
	}
	
	public SearchRequestBuilder explain(String sql) throws SQLFeatureNotSupportedException, SqlParseException {
		SearchRequestBuilder select = (SearchRequestBuilder)searchDao.explain(sql);
		logger.debug(select);
		return select;
	}
	
	private String getClusterName(String[] addresses) {
		String r = StringUtils.EMPTY;
		for (String address : addresses) {
			String cluster_name = getClusterName(address);
			if (!StringUtils.isEmpty(address)) {
				if (StringUtils.isEmpty(r)) {
					r = cluster_name;
				} else if (!StringUtils.equals(r, cluster_name)) {
					throw new RuntimeException("Elasticsearch servers are not in same cluster!");
				}
			}
		}
		return r;
	}

	private String getClusterName(String address) {
		Rest rest = new Rest();
		JSONObject json = rest.get(String.format("http://%s/", address));
		return json.getString("cluster_name");
	}
}
//	if (StringUtils.containsIgnoreCase(sql, "SHOW VARIABLES")
//	|| StringUtils.containsIgnoreCase(sql, "SELECT @@session.auto_increment_increment")
//	|| StringUtils.containsIgnoreCase(sql, "show databases")
//	|| StringUtils.containsIgnoreCase(sql, "SELECT database()")
//	|| StringUtils.containsIgnoreCase(sql, "FROM INFORMATION_SCHEMA.STATISTICS.")
//	|| StringUtils.containsIgnoreCase(sql, "from mysql.")) {
//
//return schemaManager.query(sql);
//} else if (StringUtils.equalsIgnoreCase(sql, "SELECT * FROM persons")) {
//ResultSet rs = new ResultSet();
//
//Column col = new Column("Fake Data");
//rs.addColumn(col);
//
//Column col2 = new Column("Fake Date");
//rs.addColumn(col2);
//
//Row row = new Row();
//row.addData("1");
//row.addData(new Date().getTime());
//
//rs.addRow(row); 
//return rs;
//}

	//private SchemaManager schemaManager;
//private List<String> hosts = new ArrayList<String>();
//public void setSchemaManager(SchemaManager schemaManager) {
//	this.schemaManager = schemaManager;
//	this.schemaManager.setHosts(hosts);
//}

//public void setHosts(String hosts) {
//	String[] ary = hosts.split(",");
//	for (String s : ary) {
//		if (!s.trim().isEmpty()) {
//			this.hosts.add(s.trim());
//		}
//	}
//}

//Aggregations aggregations = response.getAggregations();
//if (aggregations == null) {
//	SearchHit[] hits = response.getHits().getHits();
//	
//	
//	int index = 0;
//	for (SearchHit hit : hits) {
//		Map<String, Object> map = hit.getSource();
//		
//		if (index == 0) {
//			appendColumn(rs, map);
//		}
//		index ++;
//		
//		
//	}
//} else {
//	
//	Map<String, Aggregation> map = aggregations.getAsMap();
//	map = aggregations.asMap();
//	for (String key : map.keySet()) {
//		logger.debug(key);
//		Aggregation aggregation = aggregations.get(key); 
//		logger.debug(aggregation.getClass() + ", " + aggregation.getName());
//	}
//	
//	Iterator<Aggregation> iter = aggregations.iterator();
//	while (iter.hasNext()) {
//		Aggregation aggregation = iter.next();
//		String name = aggregation.getName();
//		logger.debug(name);
//	}
//}


