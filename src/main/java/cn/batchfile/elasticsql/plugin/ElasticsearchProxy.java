package cn.batchfile.elasticsql.plugin;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import cn.batchfile.elasticsql.elasticsearch.StatementExecutor;
import cn.batchfile.elasticsql.schema.SchemaManager;

import com.github.mpjct.jmpjct.Engine;
import com.github.mpjct.jmpjct.JMP;
import com.github.mpjct.jmpjct.mysql.proto.Column;
import com.github.mpjct.jmpjct.mysql.proto.Flags;
import com.github.mpjct.jmpjct.mysql.proto.ResultSet;
import com.github.mpjct.jmpjct.mysql.proto.Row;
import com.github.mpjct.jmpjct.plugin.Base;

public class ElasticsearchProxy extends Base {
	public Logger logger = Logger.getLogger("Plugin.ElasticsearchCover");
	private StatementExecutor statementExecutor;

	public void init(Engine context) throws IOException {
		
		//connect to schema database
		String db_url = JMP.config.getProperty("schemaUrl");
		String db_username = JMP.config.getProperty("schemaUsername");
		String db_password = JMP.config.getProperty("schemaPassword");
		SchemaManager schemaManager = new SchemaManager();
		schemaManager.connect(db_url, db_username, db_password);
		
		//connect to elasticsearch cluster
		String hosts = JMP.config.getProperty("elasticsearchHosts");
		statementExecutor = new StatementExecutor();
		statementExecutor.setSchemaManager(schemaManager);
		statementExecutor.setHosts(hosts);
	}
	
	/**
	 * compose handshake package and send to client
	 */
	public void read_handshake(Engine context) {
		this.logger.trace("read_handshake");
		/*
		context.buffer.clear();
//		context.handshake = new Handshake();
		context.handshake.authPluginDataLength = 21;
		context.handshake.authPluginName = "mysql_native_password";
		context.handshake.capabilityFlags = 4160716815L;
//		context.handshake.challenge1 = "j@=RcyZ4";
//		context.handshake.challenge2 = "7T%Xj/GZn<!, ";
		context.handshake.serverVersion = "5.5.28";
		context.handshake.characterSet = 8;
//		context.handshake.connectionId = 102;
		context.handshake.protocolVersion = 10;
		context.handshake.sequenceId = 0;
		context.handshake.statusFlags = 2;
		
		ResultSet.characterSet = context.handshake.characterSet;
		context.buffer.add(context.handshake.toPacket());
		*/
	}

	/**
	 * compose auth result package and send to client
	 */
    public void read_auth_result(Engine context) throws IOException {
        this.logger.trace("read_auth_result");
//        context.buffer.clear();
//        OK ok = new OK();
//        ok.affectedRows = 0;
//        ok.lastInsertId= 0;
//        ok.sequenceId = 2;
//        ok.statusFlags = 2;
//        ok.warnings = 0;
//        context.buffer.add(ok.toPacket());
    }
    
    public void read_query(Engine context) throws IOException {
    	this.logger.trace("read_query");
    	String sql = context.query;
    	
    	ResultSet rs = statementExecutor.execute(sql);
    	if (rs != null) {
            context.clear_buffer();
            context.buffer = rs.toPackets();
            context.nextMode = Flags.MODE_SEND_QUERY_RESULT;
    	}
    }
}
