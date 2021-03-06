package org.squirrelnest.fairies.procedure;

import org.squirrelnest.fairies.decorator.Decorator;
import org.squirrelnest.fairies.domain.HashCode160;
import org.squirrelnest.fairies.domain.Record;
import org.squirrelnest.fairies.router.RouterTable;
import org.squirrelnest.fairies.service.RequestSendService;
import org.squirrelnest.fairies.thread.CallableWithInform;
import org.squirrelnest.fairies.thread.inform.OneResponseInform;
import org.squirrelnest.fairies.thread.inform.interfaces.Inform;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Inoria on 2019/3/16.
 */
public class FindNode extends AbstractProcedure<List<Record>> {

    final static String DECORATE_KEY_REQUEST = "requested";
    final static String DECORATE_KEY_RESPONSE = "response";

    private final ExecutorService threadPool;
    private final HashCode160 localId;

    private List<Record> startNodes;
    private Set<Decorator<Record>> proceedNodes = new HashSet<>(16);

    private int lastMinDistance;

    FindNode(HashCode160 localId, HashCode160 targetId, int k, int alpha, int requestTimeoutMs,
                    RouterTable routerTable, RequestSendService sendService) {
        super(targetId, k, alpha, requestTimeoutMs, routerTable, sendService);
        this.localId = localId;
        startNodes = routerTable.getNearNodes(targetId);
        threadPool = new ThreadPoolExecutor(this.alpha, this.alpha * 4,
                3, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    /**
     * 查询逻辑：从初始节点开始并发查询，每当有结果返回时，将到目标的距离小于初始节点的节点们加入查询池，并记录结果中的最小距离
     * 从池中再挑选出alpha个未发送过请求的节点进行查询，只保存距离小于上一轮获得的最小距离的节点
     * 当池中节点全部已收到回复或未响应后，返回k个最近的节点
     */
    @Override
    public List<Record> execute() {

        Inform<Boolean> informer = new OneResponseInform();
        proceedNodes.addAll(Decorator.decoratorSet(new HashSet<>(startNodes)));

        //多线程连续查询主要逻辑
        while(true) {
            lastMinDistance = calculateMinDistance(proceedNodes, targetId);
            Set<Decorator<Record>> noRequestNodes = getNoRequestNodes(proceedNodes);
            if (noRequestNodes.isEmpty() && getNoResponseNodes(proceedNodes, requestTimeoutMs).isEmpty()) {
                break;
            }
            for (Decorator<Record> record : noRequestNodes) {
                queryOneNode(record, informer);
            }
            blockUntilOneResponse(informer, requestTimeoutMs);
        }

        //此时还没有返回的目标节点，视为连接失效
        threadPool.shutdownNow();

        //获取结果中k个离目标最近的节点，返回，至此完成了findNode过程。
        return findKNearestNodesFromProceed(proceedNodes, targetId, k);
    }

    static List<Record> findKNearestNodesFromProceed(Set<Decorator<Record>> proceeds, HashCode160 target, int k) {
        List<Decorator<Record>> sortList = new ArrayList<>(proceeds);
        sortList.sort((decorator1, decorator2) -> {
            Integer distance1 = decorator1.getData().getNodeId().calculateDistance(target);
            Integer distance2 = decorator2.getData().getNodeId().calculateDistance(target);
            Boolean response1 = (Boolean)decorator1.getOrDefault(DECORATE_KEY_RESPONSE, false);
            Boolean response2 = (Boolean)decorator2.getOrDefault(DECORATE_KEY_RESPONSE, false);
            if (distance1.equals(distance2)) {
                return response2.compareTo(response1);
            }
            return distance1.compareTo(distance2);
        });
        int totalLength = sortList.size();
        int limit = totalLength < k ? totalLength : k;

        return Decorator.unDecorate(sortList.subList(0, limit));
    }

    private void queryOneNode(Decorator<Record> receiver, Inform<Boolean> informer) {
        if (receiver.getData().getNodeId().equals(localId)) {
            return;
        }
        CallableWithInform<List<Record>, Boolean> task = new CallableWithInform<List<Record>, Boolean>() {
            @Override
            public List<Record> originCall() throws Exception {
                receiver.put(DECORATE_KEY_REQUEST, System.currentTimeMillis());

                List<Record> queryResult = requestSendService.requestNearestNodes(receiver.getData(), targetId);
                receiver.put(DECORATE_KEY_RESPONSE, true);

                routerTable.knowNodes(queryResult, false);
                List<Record> filtered = filterNearerRecords(queryResult, targetId, lastMinDistance);
                proceedNodes.addAll(Decorator.decoratorSet(new HashSet<>(filtered)));
                return queryResult;
            }
        };
        task.setInform(informer);
        threadPool.submit(task);
    }

    //
    //Methods below can be reused in findValue procedure~
    //

    static Set<Decorator<Record>> getNoRequestNodes(Set<Decorator<Record>> proceedNodes) {
        //For thread safe.
        Set<Decorator<Record>> view = new HashSet<>(proceedNodes);
        Set<Decorator<Record>> result = new HashSet<>(16);
        for(Decorator<Record> decorator : view) {
            Long requestTime = (Long)decorator.get(DECORATE_KEY_REQUEST);
            if(requestTime == null) {
                result.add(decorator);
            }
        }
        return result;
    }

    static Set<Decorator<Record>> getNoResponseNodes(Set<Decorator<Record>> proceedNodes, int timeout) {
        //For thread safe.
        Set<Decorator<Record>> view = new HashSet<>(proceedNodes);
        Set<Decorator<Record>> result = new HashSet<>(16);
        Long currentTime = System.currentTimeMillis();
        for(Decorator<Record> decorator : view) {
            Boolean hasResponse = (Boolean)decorator.getOrDefault(DECORATE_KEY_RESPONSE, false);
            Long requestTime = (Long)decorator.getOrDefault(DECORATE_KEY_REQUEST, currentTime);
            if(!hasResponse && currentTime - requestTime <= timeout) {
                result.add(decorator);
            }
        }
        return result;
    }

    static int calculateMinDistance(Collection<Decorator<Record>> range, HashCode160 target) {
        int minDistance = 161;
        for(Decorator<Record> record : range) {
            int distance = record.getData().getNodeId().calculateDistance(target);
            if(distance < minDistance) {
                minDistance = distance;
            }
        }
        return minDistance;
    }

    //将通知器状态设置为false，即会阻塞当前线程，直到任意查询线程返回结果时将状态置为true或者超过最长等待时间为止
    static void blockUntilOneResponse(Inform<Boolean> informer, int timeout) {
         informer.setState(false);
         informer.blockUntilState(true, (long)timeout);
    }

    static List<Record> filterNearerRecords(List<Record> source, HashCode160 targetId, int lastMinDistance) {
        List<Record> result = new ArrayList<>(source.size());
        for (Record record : source) {
            if (record.getNodeId().calculateDistance(targetId) < lastMinDistance) {
                result.add(record);
            }
        }
        return result;
    }
}
