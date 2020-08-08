/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.remoting.RPCHook;

public class MQClientManager {
    private final static InternalLogger log = ClientLogger.getLog();
    private static MQClientManager instance = new MQClientManager();
    private AtomicInteger factoryIndexGenerator = new AtomicInteger();
    private ConcurrentMap<String/* clientId */, MQClientInstance> factoryTable =
        new ConcurrentHashMap<String, MQClientInstance>();

    private MQClientManager() {

    }

    public static MQClientManager getInstance() {
        return instance;
    }

    public MQClientInstance getOrCreateMQClientInstance(final ClientConfig clientConfig) {
        return getOrCreateMQClientInstance(clientConfig, null);
    }

    public MQClientInstance getOrCreateMQClientInstance(final ClientConfig clientConfig, RPCHook rpcHook) {
        // 构建 MQClientId。
        String clientId = clientConfig.buildMQClientId();

        /**
         * 检测是否已经存在。
         *
         * clientId 为客服端 IP + instance + （unitname可选），
         * 那么如果同一台物理服务器部署俩个应用程序，
         * 应用程序岂不是 clientId 相同，会造成混乱？
         *
         * 为了避免这个问题，如果 instance 为默认值 DEFAULT 的话，
         * RocketMQ 会自动将 instance 设置为进程 ID，这样避免了不同进程的相互影响，
         * 但同一个 JVM 中的不同消费者和不同生产者在启动时获取到的 MQClientInstance 实例是同一个。
         *
         * MQClientInstance 中封装了 RocketMQ 网络处理 API，是消息生产者、消费者 与 NameServer、Broker 打交道的网络通道。
         */
        MQClientInstance instance = this.factoryTable.get(clientId);
        if (null == instance) {
            /**
             * 在此解释一下下面实例化第三个参数，钩子函数。
             *
             * 钩子（Hook）概念源于Windows的消息处理机制，通过设置钩子，应用程序对所有消息事件进行拦截，然后执行钩子函数。
             * 根据概念感觉类似 AOP 这样子，会对消息时间进行拦截，然后执行钩子函数。
             * AOP 也是通过拦截器拦截然后对方法进行增强，从而实现代理。
             */
            instance =
                new MQClientInstance(clientConfig.cloneClientConfig(),
                    this.factoryIndexGenerator.getAndIncrement()/*自增的索引，或者说是 Id，是 int 的 原子类型*/,
                        clientId,
                        rpcHook/*钩子函数*/);

            /**
             * factoryTable 是一个 ConcurrentMap<String（clientId）, MQClientInstance>，
             * 也就是同一个 clientId 只会创建一个 MQClientInstance。
             */
            MQClientInstance prev = this.factoryTable.putIfAbsent(clientId, instance);
            if (prev != null) {
                instance = prev;
                log.warn("Returned Previous MQClientInstance for clientId:[{}]", clientId);
            } else {
                log.info("Created new MQClientInstance for clientId:[{}]", clientId);
            }
        }

        return instance;
    }

    public void removeClientFactory(final String clientId) {
        this.factoryTable.remove(clientId);
    }
}
