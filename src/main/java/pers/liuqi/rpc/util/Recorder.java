package pers.liuqi.rpc.util;

/**
 * @author LiuQi - [Created on 2018-08-15]
 */
public class Recorder {


//    private static Map<String, ElapsedTimeRecorder> serviceRecorderMap = new HashMap<>();
//    private static Map<String, Map<String, ElapsedTimeRecorder>> methodRecorderMap = new HashMap<>();
//    private static Map<Long, Triple<String, String, Long>> startTimeRecorderMap = new HashMap<>();

    public static void record(String serviceName, String method, long cost) {
        //记录每个服务的耗时
//        ElapsedTimeRecorder serviceRecorder = serviceRecorderMap.computeIfAbsent(serviceName, ElapsedTimeRecorder::new);
//        serviceRecorder.addElapsedTime(cost);
//
//        //详细记录每个具体服务方法的耗时
//        Map<String, ElapsedTimeRecorder> methodMap = methodRecorderMap.computeIfAbsent(serviceName, key -> CollectionUtil.newHashMap());
//        ElapsedTimeRecorder methodRecorder = methodMap.computeIfAbsent(method, key -> new ElapsedTimeRecorder(serviceName + "#" + method));
//        methodRecorder.addElapsedTime(cost);
    }

    public static void invokeStart(String serviceName, String method, long invokeId) {
//        Triple<String, String, Long> record = Triple.of(serviceName, method, System.nanoTime());
//        startTimeRecorderMap.put(invokeId, record);
    }

    public static void invokeFinish(long invokeId) {
//        Triple<String, String, Long> record = startTimeRecorderMap.remove(invokeId);
//        if (record != null) {
//            long cost = System.nanoTime() - record.getRight();
//            record(record.getLeft(), record.getMiddle(), cost);
//        } else {

//        }
    }

    public static void printRecord() {
//        RuntimeLogger.info(ElapsedTimeRecorder.toShowString("Service Record ", serviceRecorderMap.values()));
//        RuntimeLogger.info(ElapsedTimeRecorder.toShowString("Method Record ", methodRecorderMap.values().stream().flatMap(map -> map.values().stream()).collect(Collectors.toList())));
    }
}

