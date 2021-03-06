/*******************************************************************************
 * Copyright (c) 2015 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.formatting2.regionaccess.internal;

import org.eclipse.xtext.formatting2.regionaccess.ISemanticRegion;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

/**
 * @author Moritz Eysholdt - Initial contribution and API
 */
public class SemanticRegionMatcher extends AbstractSemanticRegionsFinder {

	private final ISemanticRegion region;

	public SemanticRegionMatcher(ISemanticRegion region) {
		super();
		this.region = region;
	}

	@Override
	protected ImmutableList<ISemanticRegion> findAll(Predicate<ISemanticRegion> predicate) {
		ISemanticRegion element = findFirst(predicate);
		if (element == null)
			return ImmutableList.of();
		else
			return ImmutableList.of(element);
	}

	@Override
	protected ISemanticRegion findFirst(Predicate<ISemanticRegion> predicate) {
		if (predicate.apply(region))
			return region;
		return null;
	}
}
