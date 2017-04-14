package cn.com.warlock.emitter.zk;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.util.ArrayList;
import java.util.List;

public class ZooKeeperHelper {

    static void mkdirp(ZooKeeper zookeeper, String znode) throws KeeperException,
                                                          InterruptedException {
        boolean createPath = false;
        for (String path : pathParts(znode)) {
            if (!createPath) {
                Stat stat = zookeeper.exists(path, false);
                if (stat == null) {
                    createPath = true;
                }
            }
            if (createPath) {
                create(zookeeper, path);
            }
        }
    }

    static void create(ZooKeeper zookeeper, String znode) throws KeeperException,
                                                          InterruptedException {
        zookeeper.create(znode, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    static void create(ZooKeeper zookeeper, String znode, byte[] value) throws KeeperException,
                                                                        InterruptedException {
        zookeeper.create(znode, value, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
    }

    static void createIfNotThere(ZooKeeper zookeeper, String znode) throws KeeperException,
                                                                    InterruptedException {
        try {
            create(zookeeper, znode);
        } catch (KeeperException e) {
            if (e.code() != KeeperException.Code.NODEEXISTS) {
                throw e;
            }
        }
    }

    static List<String> pathParts(String path) {
        String[] pathParts = path.split("/");
        List<String> parts = new ArrayList<>(pathParts.length);
        String pathSoFar = "";
        for (String pathPart : pathParts) {
            if (!pathPart.equals("")) {
                pathSoFar += "/" + pathPart;
                parts.add(pathSoFar);
            }
        }
        return parts;
    }
}
