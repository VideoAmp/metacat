/*
 *
 *  Copyright 2016 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.metacat.main.services.notifications.sns

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.NotFoundException
import com.amazonaws.services.sns.model.PublishResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.google.common.collect.Lists
import com.netflix.metacat.common.MetacatRequestContext
import com.netflix.metacat.common.QualifiedName
import com.netflix.metacat.common.dto.PartitionDto
import com.netflix.metacat.common.dto.PartitionsSaveResponseDto
import com.netflix.metacat.common.dto.TableDto
import com.netflix.metacat.common.dto.notifications.sns.messages.AddPartitionMessage
import com.netflix.metacat.common.dto.notifications.sns.messages.CreateTableMessage
import com.netflix.metacat.common.dto.notifications.sns.messages.DeletePartitionMessage
import com.netflix.metacat.common.dto.notifications.sns.messages.DeleteTableMessage
import com.netflix.metacat.common.dto.notifications.sns.messages.UpdateTableMessage
import com.netflix.metacat.common.dto.notifications.sns.messages.UpdateTablePartitionsMessage
import com.netflix.metacat.common.server.Config
import com.netflix.metacat.common.server.events.MetacatCreateTablePostEvent
import com.netflix.metacat.common.server.events.MetacatDeleteTablePartitionPostEvent
import com.netflix.metacat.common.server.events.MetacatDeleteTablePostEvent
import com.netflix.metacat.common.server.events.MetacatRenameTablePostEvent
import com.netflix.metacat.common.server.events.MetacatSaveTablePartitionPostEvent
import com.netflix.metacat.common.server.events.MetacatUpdateTablePostEvent
import spock.lang.Specification

/**
 * Tests for the SNSNotificationServiceImpl.
 *
 * @author tgianos
 * @since 0.1.47
 */
class SNSNotificationServiceImplSpec extends Specification {

    def client = Mock(AmazonSNSAsyncClient)
    def qName = QualifiedName.fromString(
        UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
    )
    def mapper = Mock(ObjectMapper)
    def config = Mock(Config)
    def partitionArn = UUID.randomUUID().toString()
    def tableArn = UUID.randomUUID().toString()
    def service =
        new SNSNotificationServiceImpl(this.client, this.tableArn, this.partitionArn, this.mapper, this.config)

    def "Will Notify On Partition Creation"() {
        def partitions = Lists.newArrayList(new PartitionDto(), new PartitionDto(), new PartitionDto())

        def event = new MetacatSaveTablePartitionPostEvent(
            this.qName,
            Mock(MetacatRequestContext),
            partitions,
            Mock(PartitionsSaveResponseDto)
        )

        when:
        this.service.notifyOfPartitionAddition(event)

        then:
        1 * this.config.isSnsNotificationTopicPartitionEnabled() >> true
        3 * this.mapper.writeValueAsString(_ as AddPartitionMessage) >> UUID.randomUUID().toString()
        1 * this.mapper.writeValueAsString(_ as UpdateTablePartitionsMessage) >> UUID.randomUUID().toString()
        3 * this.client.publishAsync(this.partitionArn, _ as String, _ as AsyncHandler) >> new PublishResult()
        1 * this.client.publishAsync(this.tableArn, _ as String, _ as AsyncHandler) >> new PublishResult()
    }

    def "Will Notify On Partition Deletion"() {
        def partitions = Lists.newArrayList(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        )

        def event = new MetacatDeleteTablePartitionPostEvent(
            this.qName,
            Mock(MetacatRequestContext),
            partitions
        )

        when:
        this.service.notifyOfPartitionDeletion(event)

        then:
        1 * this.config.isSnsNotificationTopicPartitionEnabled() >> true
        5 * this.mapper.writeValueAsString(_ as DeletePartitionMessage) >> UUID.randomUUID().toString()
        1 * this.mapper.writeValueAsString(_ as UpdateTablePartitionsMessage) >> UUID.randomUUID().toString()
        5 * this.client.publishAsync(this.partitionArn, _ as String, _ as AsyncHandler) >> new PublishResult()
        1 * this.client.publishAsync(this.tableArn, _ as String, _ as AsyncHandler) >> new PublishResult()
    }

    def "Will Notify On Table Creation"() {
        def event = new MetacatCreateTablePostEvent(
            this.qName,
            Mock(MetacatRequestContext),
            new TableDto()
        )

        when:
        this.service.notifyOfTableCreation(event)

        then:
        1 * this.mapper.writeValueAsString(_ as CreateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publishAsync(this.tableArn, _ as String, _ as AsyncHandler) >> new PublishResult()
    }

    def "Will Notify On Table Deletion"() {
        def event = new MetacatDeleteTablePostEvent(
            this.qName,
            Mock(MetacatRequestContext),
            new TableDto()
        )

        when:
        this.service.notifyOfTableDeletion(event)

        then:
        1 * this.mapper.writeValueAsString(_ as DeleteTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publishAsync(this.tableArn, _ as String, _ as AsyncHandler) >> new PublishResult()
    }

    def "Will Notify On Table Rename"() {
        def event = new MetacatRenameTablePostEvent(
            this.qName,
            Mock(MetacatRequestContext),
            new TableDto(),
            new TableDto()
        )

        when:
        this.service.notifyOfTableRename(event)

        then:
        2 * this.mapper.valueToTree(_ as TableDto) >> new TextNode(UUID.randomUUID().toString())
        1 * this.mapper.writeValueAsString(_ as UpdateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publishAsync(this.tableArn, _ as String, _ as AsyncHandler) >> new PublishResult()
    }

    def "Will Notify On Table Update"() {
        def event = new MetacatUpdateTablePostEvent(
            this.qName,
            Mock(MetacatRequestContext),
            new TableDto(),
            new TableDto()
        )

        when:
        this.service.notifyOfTableUpdate(event)

        then:
        2 * this.mapper.valueToTree(_ as TableDto) >> new TextNode(UUID.randomUUID().toString())
        1 * this.mapper.writeValueAsString(_ as UpdateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publishAsync(this.tableArn, _ as String, _ as AsyncHandler) >> new PublishResult()
    }

    def "Won't retry on Other Exception"() {
        def event = new MetacatCreateTablePostEvent(
            this.qName,
            Mock(MetacatRequestContext),
            new TableDto()
        )

        when:
        this.service.notifyOfTableCreation(event)

        then:
        1 * this.mapper.writeValueAsString(_ as CreateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publishAsync(this.tableArn, _ as String, _ as AsyncHandler) >> { throw new NotFoundException("Exception") }
    }
}
