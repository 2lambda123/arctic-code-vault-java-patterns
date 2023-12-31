/** (rank 93) copied from https://github.com/apache/zookeeper/blob/83d79d16d683dbf07a8a1b52ed97558530f92f89/zookeeper-contrib/zookeeper-contrib-loggraph/src/main/java/org/apache/zookeeper/graph/JsonGenerator.java
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class JsonGenerator {
	private final ObjectMapper mapper = new ObjectMapper();
	private final JsonNode root;
    private final Set<Integer> servers;

    private class Message {
	private int from;
	private int to;
	private long zxid;

	public Message(int from, int to, long zxid) {
	    this.from = from;
	    this.to = to;
	    this.zxid = zxid;
	}
	
	public boolean equals(Message m) {
	    return (m.from == this.from 
		    && m.to == this.to
		    && m.zxid == this.zxid);
	}
    };

    public JsonNode txnEntry(TransactionEntry e) {
		JsonNode event = mapper.createObjectNode();

		((ObjectNode) event).put("time", Long.toString(e.getTimestamp()));
		((ObjectNode) event).put("client", Long.toHexString(e.getClientId()));
		((ObjectNode) event).put("cxid", Long.toHexString(e.getCxid()));
		((ObjectNode) event).put("zxid", Long.toHexString(e.getZxid()));
		((ObjectNode) event).put("op", e.getOp());
		((ObjectNode) event).put("extra", e.getExtra());
		((ObjectNode) event).put("type", "transaction");

		return event;
    }

    /**
       Assumes entries are sorted by timestamp.
     */
    public JsonGenerator(LogIterator iter) {
		servers = new HashSet<Integer>();

		Pattern stateChangeP = Pattern.compile("- (LOOKING|FOLLOWING|LEADING)");
		Pattern newElectionP = Pattern.compile("New election. My id =  (\\d+), Proposed zxid = (\\d+)");
		Pattern receivedProposalP = Pattern.compile("Notification: (\\d+) \\(n.leader\\), (\\d+) \\(n.zxid\\), (\\d+) \\(n.round\\), .+ \\(n.state\\), (\\d+) \\(n.sid\\), .+ \\(my state\\)");
		Pattern exceptionP = Pattern.compile("xception");

		root = mapper.createObjectNode();
		Matcher m = null;
		ArrayNode events = mapper.createArrayNode();
		((ObjectNode)root).set("events", events);

		long starttime = Long.MAX_VALUE;
		long endtime = 0;

		int leader = 0;
		long curEpoch = 0;

		while (iter.hasNext()) {
			LogEntry ent = iter.next();

			if (ent.getTimestamp() < starttime) {
				starttime = ent.getTimestamp();
			}
			if (ent.getTimestamp() > endtime) {
				endtime = ent.getTimestamp();
			}

			if (ent.getType() == LogEntry.Type.TXN) {
				events.add(txnEntry((TransactionEntry)ent));
			}
			else {
				Log4JEntry e = (Log4JEntry)ent;
				servers.add(e.getNode());

				if ((m = stateChangeP.matcher(e.getEntry())).find()) {
					JsonNode stateChange = add("stateChange", e.getTimestamp(), e.getNode(), m.group(1));
					events.add(stateChange);

					if (m.group(1).equals("LEADING")) {
					leader = e.getNode();
					}
				}
				else if ((m = newElectionP.matcher(e.getEntry())).find()) {
					Iterator<Integer> iterator = servers.iterator();
					long zxid = Long.valueOf(m.group(2));
					int count = (int)zxid;// & 0xFFFFFFFFL;
					int epoch = (int)Long.rotateRight(zxid, 32);// >> 32;

					if (leader != 0 && epoch > curEpoch) {
						JsonNode stateChange = add("stateChange", e.getTimestamp(), leader, "INIT");
						events.add(stateChange);

						leader = 0;
					}

					while (iterator.hasNext()) {
						int dst = iterator.next();
						if (dst != e.getNode()) {
							JsonNode msg = mapper.createObjectNode();
							((ObjectNode)msg).put("type", "postmessage");
							((ObjectNode)msg).put("src", e.getNode());
							((ObjectNode)msg).put("dst", dst);
							((ObjectNode)msg).put("time", e.getTimestamp());
							((ObjectNode)msg).put("zxid", m.group(2));
							((ObjectNode)msg).put("count", count);
							((ObjectNode)msg).put("epoch", epoch);

							events.add(msg);
						}
					}
				}
				else if ((m = receivedProposalP.matcher(e.getEntry())).find()) {
					// Pattern.compile("Notification: \\d+, (\\d+), (\\d+), \\d+, [^,]*, [^,]*, (\\d+)");//, LOOKING, LOOKING, 2
					int src = Integer.valueOf(m.group(4));
					long zxid = Long.valueOf(m.group(2));
					int dst = e.getNode();
					long epoch2 = Long.valueOf(m.group(3));

					int count = (int)zxid;// & 0xFFFFFFFFL;
					int epoch = (int)Long.rotateRight(zxid, 32);// >> 32;

					if (leader != 0 && epoch > curEpoch) {
						JsonNode stateChange = add("stateChange", e.getTimestamp(), leader, "INIT");
						events.add(stateChange);

						leader = 0;
					}

					if (src != dst) {
						JsonNode msg = mapper.createObjectNode();
						((ObjectNode)msg).put("type", "delivermessage");
						((ObjectNode)msg).put("src", src);
						((ObjectNode)msg).put("dst", dst);
						((ObjectNode)msg).put("time", e.getTimestamp());
						((ObjectNode)msg).put("zxid", zxid);
						((ObjectNode)msg).put("count", count);
						((ObjectNode)msg).put("epoch", epoch);
						((ObjectNode)msg).put("epoch2", epoch2);

					events.add(msg);
					}
				}
				else if ((m = exceptionP.matcher(e.getEntry())).find()) {
					JsonNode ex = mapper.createObjectNode();
					((ObjectNode)ex).put("type", "exception");
					((ObjectNode)ex).put("time", e.getTimestamp());
					((ObjectNode)ex).put("server", e.getNode());
					((ObjectNode)ex).put("text", e.getEntry());
					events.add(ex);
				}
			}
			JsonNode ex = mapper.createObjectNode();
			((ObjectNode)ex).put("type", "text");
			((ObjectNode)ex).put("time", ent.getTimestamp());
			String txt = ent.toString();
			((ObjectNode)ex).put("text", txt);
			events.add(ex);
		}
		//	System.out.println("pending messages: "+pendingMessages.size());
		((ObjectNode)root).put("starttime", starttime);
		((ObjectNode)root).put("endtime", endtime);

		ArrayNode serversarray = mapper.createArrayNode();
		((ObjectNode)root).set("servers", serversarray);

		Iterator<Integer> iterator = servers.iterator();
		while (iterator.hasNext()) {
			serversarray.add(iterator.next());
		}
    }

    private JsonNode add(String type, long timestamp, int node, String entry){
		JsonNode stateChange = mapper.createObjectNode();
		((ObjectNode)stateChange).put("type", type);
		((ObjectNode)stateChange).put("time", timestamp);
		((ObjectNode)stateChange).put("server", node);
		((ObjectNode)stateChange).put("state", entry);
		return stateChange;
	}

    public String toString() {
		String jsonString = null;
		try {
			jsonString = mapper.writer(new MinimalPrettyPrinter()).writeValueAsString(root);
		} catch (JsonProcessingException e) {
			jsonString = "{\"ERR\", " + e.getMessage() + "}";
		}
		return jsonString;
    }

    public static void main(String[] args) throws Exception {
		MergedLogSource src = new MergedLogSource(args);
		LogIterator iter = src.iterator();
		System.out.println(new JsonGenerator(iter));
    }
}
