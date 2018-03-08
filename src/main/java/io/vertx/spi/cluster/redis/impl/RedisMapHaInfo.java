/*
 * Copyright (c) 2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.spi.cluster.redis.impl;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import io.vertx.core.Vertx;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.NodeListener;

/**
 * CLUSTER_MAP_NAME = "__vertx.haInfo"
 * 
 * @author <a href="mailto:leo.tu.taipei@gmail.com">Leo Tu</a>
 */
public class RedisMapHaInfo extends RedisMap<String, String> {
	private static final Logger log = LoggerFactory.getLogger(RedisMapHaInfo.class);

	private final int timeToLiveSeconds;

	private final ClusterManager clusterManager;
	private RMapCache<String, String> mapAsync;
	private final RedisMapHaInfoTTLMonitor ttlMonitor;

	public RedisMapHaInfo(Vertx vertx, ClusterManager clusterManager, RedissonClient redisson, String name,
			int timeToLiveSeconds, int refreshIntervalSeconds) {
		super(vertx, redisson, name);
		this.clusterManager = clusterManager;
		this.timeToLiveSeconds = timeToLiveSeconds;
		this.ttlMonitor = new RedisMapHaInfoTTLMonitor(vertx, this.clusterManager, redisson, this, refreshIntervalSeconds);
	}

	/**
	 * @see org.redisson.codec.JsonJacksonCodec
	 * @see org.redisson.client.codec.JsonJacksonMapCodec
	 */
	@Override
	protected RMapCache<String, String> createMap(RedissonClient redisson, String name) {
		this.mapAsync = redisson.getMapCache(name, new StringCodec());
		// log.debug("mapAsync.codec.class={}", mapAsync.getCodec().getClass().getName());
		return this.mapAsync;
	}

	protected RMapCache<String, String> getMapAsync() {
		return mapAsync;
	}

	protected int getTimeToLiveSeconds() {
		return timeToLiveSeconds;
	}

	// /**
	// * @see #setTimeToLiveSeconds
	// */
	// public void disableTTL() {
	// log.debug("...");
	// setTimeToLiveSeconds(0);
	// }
	//
	// /**
	// *
	// * @param timeToLiveSeconds disable when value <= 0
	// */
	// public void setTimeToLiveSeconds(int timeToLiveSeconds) {
	// this.timeToLiveSeconds = timeToLiveSeconds;
	// }

	public void attachListener(NodeListener nodeListener) {
		ttlMonitor.attachListener(nodeListener);
	}

	public void close() {
		ttlMonitor.stop();
	}

	/**
	 * @return previous
	 */
	@Override
	public String put(String key, String value) {
		try {
			return timeToLiveSeconds > 0 ? mapAsync.put(key, value, timeToLiveSeconds, TimeUnit.SECONDS)
					: super.put(key, value);
		} catch (Exception ignore) {
			String previous = super.put(key, value);
			log.warn("retry without TTL: key: {}, value: {}, previous: {}, timeToLiveSeconds: {}, error: {}", key, value,
					previous, timeToLiveSeconds, ignore.toString());
			return previous;
		}
	}
}
