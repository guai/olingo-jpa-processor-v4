package org.apache.olingo.jpa.processor.core.filter;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.criteria.Subquery;

import org.apache.olingo.jpa.processor.core.query.JPAAbstractQuery;
import org.apache.olingo.jpa.processor.core.query.JPAFilterQuery;
import org.apache.olingo.jpa.processor.core.query.JPANavigationPropertyInfo;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceKind;
import org.apache.olingo.server.api.uri.UriResourceLambdaAll;
import org.apache.olingo.server.api.uri.UriResourceLambdaAny;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;

abstract class JPALambdaOperation extends JPAExistsOperation {

	protected final UriInfoResource member;

	JPALambdaOperation(final JPAAbstractFilterProcessor jpaComplier, final UriInfoResource member) {
		super(jpaComplier);
		this.member = member;
	}

	public JPALambdaOperation(final JPAAbstractFilterProcessor jpaComplier, final Member member) {
		super(jpaComplier);
		this.member = member.getResourcePath();
	}

	@Override
	protected Subquery<?> buildFilterSubQueries() throws ODataApplicationException {
		return buildFilterSubQueries(determineExpression());
	}

	protected final Subquery<?> buildFilterSubQueries(final Expression expression) throws ODataApplicationException {
		final List<UriResource> allUriResourceParts = new ArrayList<UriResource>(uriResourceParts);
		allUriResourceParts.addAll(member.getUriResourceParts());

		// 1. Determine all relevant associations
		final List<JPANavigationPropertyInfo> naviPathList = determineAssoziations(sd, allUriResourceParts);
		JPAAbstractQuery<?> parent = root;
		final List<JPAFilterQuery> queryList = new ArrayList<JPAFilterQuery>(
				naviPathList.size());

		// 2. Create the queries and roots

		for (int i = naviPathList.size() - 1; i >= 0; i--) {
			final JPANavigationPropertyInfo naviInfo = naviPathList.get(i);
			if (i == 0) {
				queryList.add(new JPAFilterQuery(odata, sd, naviInfo.getUriResiource(), parent, em, naviInfo
						.getAssociationPath(), expression));
			} else {
				queryList.add(new JPAFilterQuery(odata, sd, naviInfo.getUriResiource(), parent, em, naviInfo
						.getAssociationPath()));
			}
			parent = queryList.get(queryList.size() - 1);
		}
		// 3. Create select statements
		Subquery<?> childQuery = null;
		for (int i = queryList.size() - 1; i >= 0; i--) {
			childQuery = queryList.get(i).getSubQueryExists(childQuery);
		}
		return childQuery;
	}

	protected Expression determineExpression() {
		for (final UriResource uriResource : member.getUriResourceParts()) {
			if (uriResource.getKind() == UriResourceKind.lambdaAny) {
				return ((UriResourceLambdaAny) uriResource).getExpression();
			} else if (uriResource.getKind() == UriResourceKind.lambdaAll) {
				return ((UriResourceLambdaAll) uriResource).getExpression();
			}
		}
		return null;
	}
}