package com.ebiznext.comet.services
import better.files._
import com.ebiznext.comet.db.RocksDBConnectionMockBaseSpec
import com.ebiznext.comet.model.CometModel.{Cluster, User}
import org.scalatest.WordSpec

import scala.util.{Failure, Success}

/**
  * Created by Mourad on 30/07/2018.
  */
class ClusterServicesSpec extends WordSpec with RocksDBConnectionMockBaseSpec {

  import scala.concurrent.ExecutionContext.Implicits.global

  val cluster1: Cluster    = Cluster.empty.copy("id1", "inventoryFile1")
  val newCluster1: Cluster = Cluster.empty.copy("id1", "inventoryFile2")
  val user1                = User("user1", Set())

  before {
    rocksdbConnection.write(user1.id, user1)
    val maybeUser: Option[User] = rocksdbConnection.read[User](user1.id)
    maybeUser match {
      case Some(user) => succeed
      case None       => fail(s"DB should at least have User ${user1.id} ")
    }
  }

  val clusterService: ClusterService = new ClusterService

  "ClusterService" when {
    "create" should {
      "return the new created Cluster id" in {
        clusterService.create(user1.id, cluster1) match {
          case Failure(exception) => fail(exception)
          case Success(v)         => v.getOrElse("") shouldBe cluster1.id
        }
      }
      "Nothing happen when Cluster object with same id already exists" in {
        clusterService.create(user1.id, cluster1)
        clusterService.create(user1.id, cluster1) match {
          case Failure(exception) => fail(exception)
          case Success(v) =>
            v match {
              case None => succeed
              case _    => fail("this should'nt happen.")
            }
        }
      }
      "throw exception if userId does not exist" in {
        clusterService.create("user2", cluster1) match {
          case Failure(_) => succeed
          case Success(_) => fail("this should'nt happen.")
        }
      }
    }

    "get" should {
      "return a Cluster object by his id when the object" in {
        clusterService.create(user1.id, cluster1)
        clusterService.get(user1.id, cluster1.id) match {
          case Failure(exception) => fail(exception)
          case Success(v) =>
            v.isDefined shouldBe true
            v.getOrElse(Cluster.empty) shouldBe cluster1
        }
      }
      "Nothing is returned when there is no ClusterID of the given userId" in {
        clusterService.create(user1.id, cluster1)
        clusterService.get(user1.id, "id2") match {
          case Failure(exception) => fail(exception)
          case Success(v)         => v.isDefined shouldBe false
        }
      }
      "throw exception if userId does not exist" in {
        clusterService.get("user2", cluster1.id) match {
          case Failure(_) => succeed
          case Success(_) => fail("this should'nt happen.")
        }
      }
    }

    "delete" should {
      "return nothing and delete the cluster instence for the given user Id" in {
        clusterService.delete(user1.id, cluster1.id) match {
          case Failure(exception) => fail(exception)
          case Success(_) =>
            clusterService.get(user1.id, cluster1.id) match {
              case Failure(exception) => fail(exception)
              case Success(v) =>
                v.isEmpty shouldBe true
            }
        }
      }
      "throw exception if userId does not exist" in {
        clusterService.delete("user2", cluster1.id) match {
          case Failure(_) => succeed
          case Success(_) => fail("this should'nt happen.")
        }
      }
    }

    "update" should {
      "return the newly updated Cluster object" in {
        clusterService.update(user1.id, cluster1.id, newCluster1) match {
          case Failure(exception) => fail(exception)
          case Success(r)         => r.get shouldBe newCluster1
        }
      }
      "Nothing to update if the given clusterId doesn't exist" in {
        clusterService.create(user1.id, cluster1)
        clusterService.update(user1.id, "id2", newCluster1) match {
          case Failure(_) => clusterService.get(user1.id, "id2").toOption.isEmpty shouldBe true
          case Success(_) => fail("this should'nt happen.")
        }
      }
      "throw exception if userId does not exist" in {
        clusterService.update("user2", cluster1.id, newCluster1) match {
          case Failure(_) => succeed
          case Success(_) => fail("this should'nt happen.")
        }
      }
    }

    "clone" should {
      "return the id of the fully cloned Cluster object" in {
        clusterService.clone(user1.id, cluster1.id, tagsOnly = false) match {
          case Failure(exception) => fail(exception)
          case Success(id) =>
            clusterService.get(user1.id, id.get) match {
              case Success(maybeCluster) =>
                maybeCluster match {
                  case Some(cluster) =>
                    cluster.id shouldBe id
                    cluster.id shouldNot be(cluster1.id)
                    cluster.inventoryFile shouldBe cluster1.inventoryFile
                    cluster.tags forall cluster1.tags shouldBe true
                    cluster.nodeGroups forall cluster1.nodeGroups shouldBe true
                    cluster.nodes forall cluster1.nodes shouldBe true

                  case None => fail(message = "We expect to have a Cluster instance!")
                }
              case Failure(exception) => fail(exception)
            }

        }
      }
      "return the id of the cloned Cluster object that contains only the tags definitions" in {
        clusterService.clone(user1.id, cluster1.id, tagsOnly = true) match {
          case Failure(exception) => fail(exception)
          case Success(id) =>
            clusterService.get(user1.id, id.get) match {
              case Success(maybeCluster) =>
                maybeCluster match {
                  case Some(cluster) =>
                    cluster.id shouldBe id
                    cluster.id shouldNot be(cluster1.id)
                    cluster.inventoryFile.isEmpty shouldBe true
                    cluster.tags forall cluster1.tags shouldBe true
                    cluster.nodeGroups.isEmpty shouldBe true
                    cluster.nodes.isEmpty shouldBe true

                  case None => fail("We expect to have a cluster instance!")
                }
              case Failure(exception) => fail(exception)
            }

        }
      }
    }
    "buildAnsibleScript" should {
      "return the path of the generated zip on the server" in {
        clusterService.buildAnsibleScript(user1.id, cluster1.id) match {
          case Failure(exception) => fail(exception)
          case Success(path)      => path.toString.toFile.extension shouldBe "zip"
        }
      }
    }
  }
}
