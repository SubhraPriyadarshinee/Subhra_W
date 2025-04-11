package com.walmart.move.nim.receiving.core.common.rest;

import org.springframework.stereotype.Component;

/**
 * Rest Connector with out any retry {@inheritDoc}
 *
 * @author sitakant
 */
@Component("restConnector")
public class SimpleRestConnector extends GenericRestConnector {}
