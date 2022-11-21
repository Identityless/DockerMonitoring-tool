package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import org.json.simple.JSONObject;


import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
    private static ObjectMapper objectMapper = new ObjectMapper(); // json string을 Map 형식의 데이터로 전환시켜주는 객
    private static JSONObject jsonObject = new JSONObject(); // json string을 쉽게 만들도록 도와주는 객체
    private static OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class); // host OS의 자원 정보를 받아오기 위한 객체
    private static List<Map<String, Object>> conDatas; // 도커 api를 이용해 여러 컨테이너에 대한 정보를 받아와 저장하는 list
    private static NetworkInfo[] networkInfos = new NetworkInfo[100]; // 각 컨테이너 별 네트워크 정보를 저장하기 위한 배얄
    private static double cpuUsageofAllContainers; // 컨테이너들의 cpu 사용량의 합을 저장하기 위한 필드
    private static long momoryUsageofAllContainers; // 컨테이너들의 메모리 사용량의 합을 저장하기 위한 필드
    private static PrintWriter printWriter; // 파일 출력용 객체
    private static String path = "~/result.txt"; // 로그 파일 저장을 위한 경로. 환경에 따라 유동적으로 변경해야 함.
    public static void main(String[] args) throws IOException, InterruptedException {
        String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/json";
        String jsonString = getJsonString(cmd, true);
        int count;
        List<JSONObject> conjsonObjectList = null;
        conDatas = objectMapper.readValue(jsonString, new TypeReference<List<Map<String, Object>>>() {});
        //System.out.println(conDatas);

        while(true) {
            printWriter = new PrintWriter(new FileWriter(path, true));
            conjsonObjectList = new ArrayList<>();
            System.out.println("--------------------Monitoring Start--------------------");
            getHostinfo(); // 호스트 정보를 받아오기 위한 메서드 호출

            count = 0;
            cpuUsageofAllContainers = 0;
            momoryUsageofAllContainers = 0;
            for (Map<String, Object> data : conDatas) {
                System.out.println("------------Container" + count + "------------");
                conjsonObjectList.add(getContainerinfo(data, count)); // 각 컨테이너 별 모니터링 정보 받아오기 위한 메서드 호출. 반환 받은 결과를 conjsonObjectList 리스트에 넣는다.
                System.out.println("----------------------------------");
                count++;
            }

            jsonObject.put("containers", conjsonObjectList); // 각 컨테이너 별 JSONObject 리스트를 메인 JSONObject에 삽입
            System.out.println("CPU Available : "+((Runtime.getRuntime().availableProcessors()/2*100)-cpuUsageofAllContainers)+" %");
            jsonObject.put("cpu_available", ((Runtime.getRuntime().availableProcessors()/2*100)-cpuUsageofAllContainers));
            System.out.println("Memory Available : "+(operatingSystemMXBean.getTotalPhysicalMemorySize()-momoryUsageofAllContainers)+" bytes");
            jsonObject.put("memory_available", (operatingSystemMXBean.getTotalPhysicalMemorySize()-momoryUsageofAllContainers));
            System.out.println("-------------------Momitoring End-------------------\n");
            System.out.println(jsonObject.toJSONString());
            printWriter.println(jsonObject.toJSONString()); // 파일 출력
            printWriter.flush();
            printWriter.close();
            Thread.sleep(2); // 2초간 정지 ( 모니터링 주기 2초 )
        }

    }

    /* 호스트 정보 검색, 출력용 메서드 */
    private static void getHostinfo(){
        System.out.println("running_container : "+conDatas.size()); // conDatas 리스트의 사이즈 = 컨테이너의 갯수
        jsonObject.put("running_container", conDatas.size());
        System.out.println("whole_cpucore : "+(Runtime.getRuntime().availableProcessors()/2*100)+"%"); // cpu core 수 * 100
        jsonObject.put("whole_cpucore", Runtime.getRuntime().availableProcessors()/2*100);
        long mem = operatingSystemMXBean.getTotalPhysicalMemorySize();  // 호스트 메모리 크기
        System.out.println("whole_mem : "+mem+" bytes");
    }

    /* 각 컨테이너 정보 검색, 출력용 메서드 */
    private static JSONObject getContainerinfo(Map<String, Object> data, int count) throws JsonProcessingException {
        String conId = data.get("Id").toString();
        String conName = data.get("Names").toString();
        conName = conName.substring(2, conName.length()-1);
        String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/"+conName+"/stats"; // 각 컨테이너 별 상태 정보를 받아오기 위한 api 요청 명령
        String statString = getJsonString(cmd, false); // 명령어 수행해서 jsonString 반환해주는 메서드 호출
        Map<String, Object> statJson = objectMapper.readValue(statString, new TypeReference<Map<String, Object>>() {});
        cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/"+conName+"/top";
        statString = getJsonString(cmd, false);
        Map<String, Object> topJson = objectMapper.readValue(statString, new TypeReference<Map<String, Object>>() {});
        List<List<Integer>> topList = (List<List<Integer>>) topJson.get("Processes");
        List<Integer> topRaw = topList.get(0);


        Map<String, Object> networkInfo = (Map<String, Object>) data.get("NetworkSettings");
        networkInfo = (Map<String, Object>) networkInfo.get("Networks");
        networkInfo = (Map<String, Object>) networkInfo.get("bridge");
        Map<String, Object> cpu_stats = (Map<String, Object>) statJson.get("cpu_stats");

        JSONObject conJsonObject = new JSONObject();

        System.out.println("PID : "+topRaw.get(1)); // 컨테이너의 pid 정보
        conJsonObject.put("pid", topRaw.get(1));

        System.out.println("IPAddress : "+networkInfo.get("IPAddress").toString()); // 컨테이너의 ip 주소
        conJsonObject.put("ip_address", networkInfo.get("IPAddress").toString());

        System.out.println("Names : "+conName); // 컨테이너 이름
        conJsonObject.put("name", conName);

        System.out.println("Container ID : " +conId); // 컨테이너 id
        conJsonObject.put("container_id", conId);

        int online_cpus = Integer.parseInt(cpu_stats.get("online_cpus").toString()); // 컨테이너에 할당 된 cpu
        System.out.println("CPU : "+online_cpus);
        conJsonObject.put("cpu", online_cpus);


        Map<String, Object> cpu_usage = (Map<String, Object>) cpu_stats.get("cpu_usage");
        Map<String, Object> precpu_stats = (Map<String, Object>) statJson.get("precpu_stats");
        Map<String, Object> precpu_usage = (Map<String, Object>) precpu_stats.get("cpu_usage");

        long cpu_delta = Long.parseLong(cpu_usage.get("total_usage").toString())-Long.parseLong(precpu_usage.get("total_usage").toString());
        long total_usage = Long.parseLong(cpu_usage.get("total_usage").toString());
        long pretotal_usage = Long.parseLong(precpu_usage.get("total_usage").toString());
        long system_cpu_usage = Long.parseLong(cpu_stats.get("system_cpu_usage").toString());
        //System.out.println(precpu_stats);
        //long system_precpu_usage = Long.parseLong(precpu_stats.get("system_cpu_usage").toString()); // 않이 이거 외 않되? -> 스펙에는 나와있는 필드가 실제로는 없다?!?!
        //long system_cpu_delta = system_cpu_usage - system_precpu_usage;
        double cpu_usage_percent = ((double)total_usage-pretotal_usage)*online_cpus*100.0/system_cpu_usage; // cpu 사용률 구하는 수식

        System.out.println("CPU Usage % : "+cpu_usage_percent+" %");
        conJsonObject.put("cpu_usage%", cpu_usage_percent);
        cpuUsageofAllContainers += cpu_usage_percent;


//        cpu_delta = cpu_stats.cpu_usage.total_usage - precpu_stats.cpu_usage.total_usage
//        system_cpu_delta = cpu_stats.system_cpu_usage - precpu_stats.system_cpu_usage
//        number_cpus = lenght(cpu_stats.cpu_usage.percpu_usage) or cpu_stats.online_cpus
//        CPU usage % = (cpu_delta / system_cpu_delta) * number_cpus * 100.0


        Map<String,Object> memory_stats = (Map<String,Object>)statJson.get("memory_stats");
        Map<String,Object> stats = (Map<String, Object>) memory_stats.get("stats");
        long used_memory = Long.parseLong(memory_stats.get("usage").toString())-Long.parseLong(stats.get("cache").toString()); //memory_stats.usage - memory_stats.stats.cache
        long available_memory = Long.parseLong(memory_stats.get("limit").toString())/8; // bit -> byte 변환을 위해 8로 나눔
        System.out.println("Available Memory : "+available_memory+" bytes");  // 사용 가능한 메모리
        conJsonObject.put("available_memory", available_memory);

        System.out.println("Used Memory :"+used_memory+" bytes"); // 사용 중인 메모리
        conJsonObject.put("used_momory", used_memory);

        System.out.println("Used Memory % : "+(((double)used_memory/available_memory)*100)+" %"); // 메모리 이용률
        conJsonObject.put("used_memory_%", (((double)used_memory/available_memory)*100));
        momoryUsageofAllContainers += used_memory;


//        used_memory = memory_stats.usage - memory_stats.stats.cache
//        available_memory = memory_stats.limit
//        Memory usage % = (used_memory / available_memory) * 100.0

        Map<String, Object> network = (Map<String, Object>)statJson.get("networks");
        Map<String, Object> eth0 = (Map<String, Object>) network.get("eth0");  // eth0 에 대한 정보를 Map 형식으로 받음


        calRxTx(eth0, count); // 모니터링 주기 별 네트워크 자원 이용량 계산을 위한 메서드 호출
        conJsonObject.put("rx_bytes", networkInfos[count].getRxbytes());
        conJsonObject.put("rx_packets", networkInfos[count].getRxpackets());
        conJsonObject.put("tx_bytes", networkInfos[count].getTxbytes());
        conJsonObject.put("tx_packets", networkInfos[count].getTxpackets());

        return conJsonObject;


    }

    /* 주기 별 네트워크 이용률 계산을 위한 메서드 */
    private static void calRxTx(Map<String, Object> eth0, int count) {
        if(networkInfos[count] == null) {   // 최초 호출된 경우 모니터링이 처음 시작되었다는 뜻이므로 새로운 NetworkInfo 객체 생성하여 배열의 해당하는 index에 삽입한 후 initalizing 문구 출력
            networkInfos[count] = new NetworkInfo();
            networkInfos[count].setRxbytes(Long.parseLong(eth0.get("rx_bytes").toString()));
            networkInfos[count].setRxpackets(Long.parseLong(eth0.get("rx_packets").toString())); // 그러나 json에서 자료 형식 맞추기 위해 JSONObject에는 그냥 네트워크 누적 이용량
            networkInfos[count].setTxbytes(Long.parseLong(eth0.get("tx_bytes").toString()));    // 삽입해준다. (최초 생성시에만 누적값이 들어감.)
            networkInfos[count].setTxpackets(Long.parseLong(eth0.get("tx_packets").toString()));
            System.out.println("Rx bytes(packet) : initializing......");
            System.out.println("Tx bytes(packet) : initializing......");
        }
        else {  // 비교 할 이전 네트워크 이용량이 있다면 모니터링 주기 사이에 이용된 네트워크 이용량을 계산할 수 있으므로 배열에서 해당하는 index에 있는 NetworkInfo 객체의 값을 이용해 새로운 네트워크 이용량을 계산.
            networkInfos[count].setRxbytes(Long.parseLong(eth0.get("rx_bytes").toString())-networkInfos[count].getRxbytes());
            networkInfos[count].setRxpackets(Long.parseLong(eth0.get("rx_packets").toString())-networkInfos[count].getRxpackets());
            networkInfos[count].setTxbytes(Long.parseLong(eth0.get("tx_bytes").toString())-networkInfos[count].getTxbytes());
            networkInfos[count].setTxpackets(Long.parseLong(eth0.get("tx_packets").toString())-networkInfos[count].getTxpackets());
            System.out.println("Rx bytes(packet) : "+networkInfos[count].getRxbytes()+"("+networkInfos[count].getRxpackets()+")");
            System.out.println("Tx bytes(packet) : "+networkInfos[count].getTxbytes()+"("+networkInfos[count].getTxpackets()+")");
        }
    }

    /* cmd 명령어 문자열을 받아 실행시킨 후 json형식의 문자열을 반환하는 메서드 */
    private static String getJsonString(String cmd, boolean mode){ // mode가 true인 경우 반환 결과 값이 여러 개인 명령어이며, false인 경우 반환 결과 값이 하나인 명령어로 동작한다.
        String s;
        String jsonString = "";
        Process p;  // 명령어 실행하기 위한 객체

        try {
            p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream())); // BufferedReader를 이용해 명령어 실행의 결과를 읽어들임
            while ((s = br.readLine()) != null && mode){
                jsonString += s;
            }
            if(!mode){
                jsonString = s;
            }
            //System.out.println(jsonString);

            p.destroy();
        } catch (IOException e) {
            System.out.println("IOException occured");
        }
        return jsonString;
    }

    /* 네트워크 이용 정보를 저장하기 위한 DTO 객체 */
    static class NetworkInfo {
        private long Rxbytes;
        private long Txbytes;
        private long Rxpackets;
        private long Txpackets;

        public NetworkInfo() { // 생성자로 최초 객체 생성 시 모든 필드 값 -1로 초기화
            Rxbytes = -1;
            Txbytes = -1;
            Rxpackets = -1;
            Txpackets = -1;
        }

        public long getRxbytes() {
            return Rxbytes;
        }

        public void setRxbytes(long rxbytes) {
            Rxbytes = rxbytes;
        }

        public long getTxbytes() {
            return Txbytes;
        }

        public void setTxbytes(long txbytes) {
            Txbytes = txbytes;
        }

        public long getRxpackets() {
            return Rxpackets;
        }

        public void setRxpackets(long rxpackets) {
            Rxpackets = rxpackets;
        }

        public long getTxpackets() {
            return Txpackets;
        }

        public void setTxpackets(long txpackets) {
            Txpackets = txpackets;
        }
    }
}
