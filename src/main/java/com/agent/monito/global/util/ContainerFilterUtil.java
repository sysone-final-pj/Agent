/**
 * 컨테이너 필터링 유틸리티 클래스
 * Agent 자신의 컨테이너를 제외하는 등의 필터링 로직을 제공
 */
package com.agent.monito.global.util;

import com.github.dockerjava.api.model.Container;
/**
 작성자: 백승준
 */
public class ContainerFilterUtil {

    private static final String AGENT_CONTAINER_NAME = "agent-monito";

    /**
     * Agent 컨테이너 여부 확인
     *
     * @param container Docker Container
     * @return Agent 컨테이너이면 true, 아니면 false
     */
    public static boolean isAgentContainer(Container container) {
        if (container == null || container.getNames() == null || container.getNames().length == 0) {
            return false;
        }
        String containerName = container.getNames()[0].replace("/", "");
        return AGENT_CONTAINER_NAME.equals(containerName);
    }

    /**
     * Agent 컨테이너가 아닌지 확인
     *
     * @param container Docker Container
     * @return Agent 컨테이너가 아니면 true, Agent 컨테이너이면 false
     */
    public static boolean isNotAgentContainer(Container container) {
        return !isAgentContainer(container);
    }

    /**
     * 컨테이너 이름이 Agent 컨테이너인지 확인
     *
     * @param containerName 컨테이너 이름
     * @return Agent 컨테이너이면 true, 아니면 false
     */
    public static boolean isAgentContainerByName(String containerName) {
        return AGENT_CONTAINER_NAME.equals(containerName);
    }
}