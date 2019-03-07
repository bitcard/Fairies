package org.squirrelnest.fairies.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.squirrelnest.fairies.domain.HashCode160;
import org.squirrelnest.fairies.storage.datasource.interfaces.KVDataSource;
import org.squirrelnest.fairies.storage.enumeration.LocalStorageTypeEnum;
import org.squirrelnest.fairies.utils.HashUtils;

import javax.annotation.Resource;

/**
 * Created by Inoria on 2019/3/7.
 */
@Service
public class LocalNodeService {

    @Resource(name = "localStorageDAO")
    private KVDataSource localStore;

    private static final String NODE_ID_KEY = "localNodeId";

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalNodeService.class);

    public HashCode160 getLocalNodeId() {
        HashCode160 localId = null;
        try {
            localId = localStore.load(LocalStorageTypeEnum.OTHER_LOCAL_STORAGE.getTypeName(), NODE_ID_KEY, HashCode160.class);
        } catch (Exception e) {
            LOGGER.error("Load local storage failed, nested local key is " + NODE_ID_KEY, e);
        }
        if (localId == null) {
            localId = HashUtils.generateLocalHash();
            try {
                localStore.save(LocalStorageTypeEnum.OTHER_LOCAL_STORAGE.getTypeName(), NODE_ID_KEY, localId);
            } catch (Exception e) {
                LOGGER.error("save local storage failed, nested local key is " + NODE_ID_KEY, e);
            }
        }
        return localId;
    }
}