package com.baidu.disconf2.client.test;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.baidu.disconf2.client.DisconfMgr;
import com.baidu.disconf2.client.store.DisconfStoreMgr;
import com.baidu.disconf2.client.test.common.BaseTestCase;
import com.baidu.disconf2.client.test.model.ConfA;
import com.baidu.disconf2.client.test.model.ServiceA;

/**
 * 
 * @author liaoqiqi
 * @version 2014-6-10
 */
public class DisconfMgrTestCase extends BaseTestCase {

    protected static final Logger LOGGER = LoggerFactory
            .getLogger(DisconfMgrTestCase.class);

    @Autowired
    private ConfA confA;

    @Autowired
    private ServiceA serviceA;

    @Test
    public void scan() {

        String packName = "com.baidu.disconf2.client";

        try {

            LOGGER.info("==============================================");
            LOGGER.info("==============================================");

            LOGGER.info(String.valueOf("varA: " + confA.getVarA()));
            LOGGER.info(String.valueOf("varA2: " + confA.getVarA2()));
            LOGGER.info(String.valueOf("varAA: " + serviceA.getVarAA()));

            DisconfMgr.start(packName);

            //
            LOGGER.info(DisconfStoreMgr.getInstance().getConfFileMap()
                    .toString());

            //
            LOGGER.info(DisconfStoreMgr.getInstance().getConfItemMap()
                    .toString());

            LOGGER.info(String.valueOf("varA: " + confA.getVarA()));
            LOGGER.info(String.valueOf("varA2: " + confA.getVarA2()));
            LOGGER.info(String.valueOf("varAA: " + serviceA.getVarAA()));

            LOGGER.info("==============================================");
            LOGGER.info("==============================================");

            Thread.sleep(10000000);

        } catch (Exception e) {

            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }
}