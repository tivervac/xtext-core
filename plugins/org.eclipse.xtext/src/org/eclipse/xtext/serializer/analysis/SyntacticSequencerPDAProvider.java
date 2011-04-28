/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.serializer.analysis;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.AbstractElement;
import org.eclipse.xtext.Action;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.CompoundElement;
import org.eclipse.xtext.CrossReference;
import org.eclipse.xtext.EnumRule;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.ParserRule;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.TerminalRule;
import org.eclipse.xtext.grammaranalysis.IGrammarNFAProvider.NFABuilder;
import org.eclipse.xtext.grammaranalysis.INFAState;
import org.eclipse.xtext.grammaranalysis.INFATransition;
import org.eclipse.xtext.grammaranalysis.IPDAState;
import org.eclipse.xtext.grammaranalysis.IPDAState.PDAStateType;
import org.eclipse.xtext.grammaranalysis.impl.AbstractCachingNFABuilder;
import org.eclipse.xtext.grammaranalysis.impl.AbstractNFAProvider;
import org.eclipse.xtext.grammaranalysis.impl.AbstractNFAState;
import org.eclipse.xtext.grammaranalysis.impl.AbstractNFATransition;
import org.eclipse.xtext.grammaranalysis.impl.AbstractPDAProvider;
import org.eclipse.xtext.grammaranalysis.impl.GrammarElementFullTitleSwitch;
import org.eclipse.xtext.serializer.ISyntacticSequencerPDAProvider;
import org.eclipse.xtext.serializer.impl.RCStack;
import org.eclipse.xtext.util.LinkedStack;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Singleton;

/**
 * @author Moritz Eysholdt - Initial contribution and API
 */
@Singleton
public class SyntacticSequencerPDAProvider implements ISyntacticSequencerPDAProvider {

	public static class SequencerNFAProvider extends AbstractNFAProvider<SequencerNFAState, SequencerNFATransition> {
		public class SequencerNFABuilder extends AbstractCachingNFABuilder<SequencerNFAState, SequencerNFATransition> {

			@Override
			public SequencerNFAState createState(AbstractElement ele) {
				return new SequencerNFAState(ele, this);
			}

			@Override
			protected SequencerNFATransition createTransition(SequencerNFAState source, SequencerNFAState target,
					boolean isRuleCall, AbstractElement loopCenter) {
				return new SequencerNFATransition(source, target, isRuleCall, loopCenter);
			}

			@Override
			public boolean filter(AbstractElement ele) {
				if (ele instanceof CompoundElement)
					return true;
				if (ele instanceof Assignment)
					return true;
				if (ele instanceof CrossReference)
					return true;
				return false;
			}

			public NFADirection getDirection() {
				return NFADirection.FORWARD;
			}
		}

		@Override
		protected NFABuilder<SequencerNFAState, SequencerNFATransition> createBuilder() {
			return new SequencerNFABuilder();
		}

		public NFABuilder<SequencerNFAState, SequencerNFATransition> getBuilder() {
			return builder;
		}

	}

	public static class SequencerNFAState extends AbstractNFAState<SequencerNFAState, SequencerNFATransition> {

		public SequencerNFAState(AbstractElement element, NFABuilder<SequencerNFAState, SequencerNFATransition> builder) {
			super(element, builder);
		}

		public List<SequencerNFATransition> collectOutgoingTransitions() {
			outgoing = Lists.newArrayList();
			outgoingRuleCalls = Lists.newArrayList();
			collectOutgoing(element, Sets.<AbstractElement> newHashSet(), false, null);
			removeDuplicates(outgoing);
			removeDuplicates(outgoingRuleCalls);
			return outgoingRuleCalls.isEmpty() ? outgoing : outgoingRuleCalls;
		}

	}

	public static class SequencerNFATransition extends AbstractNFATransition<SequencerNFAState, SequencerNFATransition> {

		public SequencerNFATransition(SequencerNFAState source, SequencerNFAState target, boolean ruleCall,
				AbstractElement loopCenter) {
			super(source, target, ruleCall, loopCenter);
		}
	}

	public static class SequencerPDAProvider extends AbstractPDAProvider<EObject> {

		protected SequencerNFAProvider nfaProvider;

		public SequencerPDAProvider(SequencerNFAProvider nfaProvider) {
			super();
			this.nfaProvider = nfaProvider;
		}

		@Override
		protected boolean canEnterRuleCall(INFAState<?, ?> state) {
			if (!(state.getGrammarElement() instanceof RuleCall))
				return false;
			RuleCall rc = (RuleCall) state.getGrammarElement();
			if (!(rc.getRule() instanceof ParserRule && rc.getRule().getType().getClassifier() instanceof EClass))
				return false;
			return GrammarUtil.containingAssignment(rc) == null;
		}

		protected boolean canReachElement(INFAState<?, ?> from, AbstractElement to, Set<Object> visited) {
			if (!visited.add(from))
				return false;
			if (from.getGrammarElement() == to)
				return true;
			for (INFATransition<?, ?> trans : from.getAllOutgoing())
				if (!trans.isRuleCall() && canReachElement(trans.getTarget(), to, visited))
					return true;
			return false;
		}

		protected boolean canReachEndState(INFAState<?, ?> from, Set<Object> visited) {
			if (!visited.add(from))
				return false;
			if (from.isEndState())
				return true;
			for (INFATransition<?, ?> trans : from.getAllOutgoing())
				if (!trans.isRuleCall() && canReachEndState(trans.getTarget(), visited))
					return true;
			return false;
		}

		protected List<INFAState<?, ?>> getActionStartFollowers(Action action) {
			ParserRule rule = GrammarUtil.containingParserRule(action);
			List<INFAState<?, ?>> result = Lists.newArrayList();
			for (INFAState<?, ?> state : getAllRuleStartFollowers(rule))
				if (canReachElement(state, action, Sets.newHashSet()))
					result.add(state);
			return result;
		}

		protected List<INFAState<?, ?>> getAllRuleStartFollowers(ParserRule pr) {
			SequencerNFAState startNfa = nfaProvider.getNFA(pr.getAlternatives());
			List<INFAState<?, ?>> result = Lists.newArrayList();
			if (nfaProvider.getBuilder().filter(pr.getAlternatives())) {
				for (SequencerNFATransition transition : startNfa.collectOutgoingTransitions())
					result.add(transition.getTarget());
			} else
				result.add(startNfa);
			for (Action a : GrammarUtil.containedActions(pr))
				if (GrammarUtil.isAssignedAction(a))
					result.add(nfaProvider.getNFA(a));
			return result;
		}

		@Override
		protected List<INFAState<?, ?>> getFollowers(EObject context, INFAState<?, ?> state, boolean returning,
				boolean canReturn) {
			List<INFAState<?, ?>> result = Lists.newArrayList();
			for (INFATransition<?, ?> transition : returning ? state.getOutgoingAfterReturn() : state.getOutgoing()) {
				if (!GrammarUtil.isAssignedAction(transition.getTarget().getGrammarElement()))
					result.add(transition.getTarget());
				if (transition.isRuleCall())
					for (Action action : GrammarUtil.containedActions(GrammarUtil.containingRule(transition.getTarget()
							.getGrammarElement())))
						if (GrammarUtil.isAssignedAction(action))
							result.add(nfaProvider.getNFA(action));
			}
			return result;
		}

		protected List<INFAState<?, ?>> getParserRuleStartFollowers(ParserRule rule) {
			List<INFAState<?, ?>> result = Lists.newArrayList();
			for (INFAState<?, ?> state : getAllRuleStartFollowers(rule))
				if (canReachEndState(state, Sets.newHashSet()))
					result.add(state);
			return result;
		}

		@Override
		protected List<INFAState<?, ?>> getStartFollowers(EObject context) {
			if (context instanceof ParserRule)
				return getParserRuleStartFollowers((ParserRule) context);
			else if (context instanceof Action)
				return getActionStartFollowers((Action) context);
			return Collections.emptyList();
		}

		@Override
		protected boolean isFinalState(EObject context, INFAState<?, ?> state, boolean returning, boolean canReturn) {
			if (context instanceof Action) {
				for (INFATransition<?, ?> transition : returning ? state.getOutgoingAfterReturn() : state.getOutgoing())
					if (transition.getTarget().getGrammarElement() == context)
						return true;
			} else if (canReturn && context instanceof ParserRule && state.isEndState()
					&& GrammarUtil.containingParserRule(state.getGrammarElement()) == context)
				return true;
			return false;

		}
	}

	protected static class SynAbsorberState extends SynState implements ISynAbsorberState {

		protected Map<AbstractElement, ISynTransition> outTransitionsByElement = Maps.newHashMap();

		protected Map<AbstractElement, ISynTransition> outTransitionsByRuleCallEnter = Maps.newHashMap();

		protected Map<AbstractElement, ISynTransition> outTransitionsByRuleCallExit = Maps.newHashMap();

		public SynAbsorberState(SynStateType type, AbstractElement element) {
			super(type, element);
		}

		protected void addTransition(ISynTransition transition) {
			addFollower(transition.getFollowers());
			switch (transition.getTarget().getType().getSimpleType()) {
				case START:
					throw new UnsupportedOperationException("StartStates can not have incoming transitions");
				case ELEMENT:
				case STOP:
					if (outTransitionsByElement.isEmpty())
						outTransitionsByElement = Maps.newHashMap();
					outTransitionsByElement.put(transition.getTarget().getGrammarElement(), transition);
					break;
				case RULECALL_ENTER:
					if (outTransitionsByRuleCallEnter.isEmpty())
						outTransitionsByRuleCallEnter = Maps.newHashMap();
					outTransitionsByRuleCallEnter.put(transition.getTarget().getGrammarElement(), transition);
					break;
				case RULECALL_EXIT:
					if (outTransitionsByRuleCallExit.isEmpty())
						outTransitionsByRuleCallExit = Maps.newHashMap();
					outTransitionsByRuleCallExit.put(transition.getTarget().getGrammarElement(), transition);
					break;
			}

		}

		public List<ISynTransition> getOutTransitions() {
			List<ISynTransition> result = Lists.newArrayList();
			result.addAll(outTransitionsByElement.values());
			result.addAll(outTransitionsByRuleCallEnter.values());
			result.addAll(outTransitionsByRuleCallExit.values());
			return result;
		}

		public Map<AbstractElement, ISynTransition> getOutTransitionsByElement() {
			return outTransitionsByElement;
		}

		public Map<AbstractElement, ISynTransition> getOutTransitionsByRuleCallEnter() {
			return outTransitionsByRuleCallEnter;
		}

		public Map<AbstractElement, ISynTransition> getOutTransitionsByRuleCallExit() {
			return outTransitionsByRuleCallExit;
		}

	}

	protected static class SynEmitterState extends SynNavigable implements ISynEmitterState {

		public SynEmitterState(SynStateType type, AbstractElement element, SynAbsorberState target) {
			super(type, element, target);
		}

	}

	protected static class SynNavigable extends SynState implements ISynNavigable {

		protected final static List<ISynState> RULE_EXIT_DEPENDENT = Lists.newArrayList();

		protected final static int UNREACHABLE = Integer.MAX_VALUE;

		protected int distanceToAbsorber = -1;

		protected Boolean involvesRuleExit;

		protected Boolean involvesUnassignedTokenRuleCalls = null;

		protected List<ISynState> shortestPathToAbsorber = null;

		protected Boolean syntacticallyAmbiguous = null;

		protected ISynAbsorberState target;

		public SynNavigable(SynStateType type, AbstractElement element, ISynAbsorberState target) {
			super(type, element);
			this.target = target;
		}

		protected int distanceTo(ISynState state, Predicate<ISynState> matches, Predicate<ISynState> bounds,
				RCStack stack, LinkedStack<ISynState> visited) {
			if (matches.apply(state))
				return 0;
			if (bounds.apply(state))
				return UNREACHABLE;
			if (visited.contains(state))
				return UNREACHABLE;
			else
				visited = visited.cloneAndPush(state);
			if (state.getType().isRuleCallExit()) {
				if (!stack.isEmpty()) {
					if (stack.peek() != state.getGrammarElement())
						return UNREACHABLE;
					else
						stack = stack.cloneAndPop();
				}
			} else if (state.getType().isRuleCallEnter())
				stack = stack.cloneAndPush((RuleCall) state.getGrammarElement());
			int dist = UNREACHABLE;
			for (ISynState follower : state.getFollowers())
				dist = Math.min(dist, distanceTo(follower, matches, bounds, stack, visited));
			if (dist != UNREACHABLE && state.getType() != SynStateType.TRANSITION)
				dist = dist + 1;
			return dist;
		}

		public int getDistanceTo(Predicate<ISynState> matches, Predicate<ISynState> bounds, RCStack stack) {
			return distanceTo(this, matches, bounds, stack, new LinkedStack<ISynState>());
		}

		public int getDistanceWithStackToAbsorber(RCStack stack) {
			if (involvesRuleExit()) {
				return getDistanceWithStackToElement(SynPredicates.absorber(), stack);
			}
			if (distanceToAbsorber < 0)
				distanceToAbsorber = getDistanceTo(SynPredicates.absorber(), SynPredicates.absorber(), stack);
			return distanceToAbsorber;
		}

		public int getDistanceWithStackToElement(Predicate<ISynState> matches, RCStack stack) {
			int result = 0;
			if (involvesRuleExit()) {
				while (!stack.isEmpty()) {
					int r = getDistanceTo(SynPredicates.ruleCallExit(stack.peek()), SynPredicates.ruleCallExits(),
							stack);
					if (r != UNREACHABLE) {
						result += r;
						stack = stack.cloneAndPop();
					} else
						break;
				}
			}
			return result + getDistanceTo(matches, SynPredicates.absorber(), stack);
		}

		public List<ISynState> getShortestPathTo(AbstractElement ele, RCStack stack, boolean addMatch) {
			return getShortestPathTo(SynPredicates.emitter(ele), SynPredicates.absorber(), stack, addMatch);
		}

		public List<ISynState> getShortestPathTo(Predicate<ISynState> matches, Predicate<ISynState> bounds,
				RCStack stack, boolean addMatch) {
			List<ISynState> routes;
			//			if (getType() == SynStateType.TRANSITION)
			routes = getFollowers();
			//			else
			//				routes = Collections.<ISynState> singletonList(this);
			List<ISynState> result = Lists.newArrayList();
			Set<ISynState> visited = Sets.newHashSet();
			ISynState next;
			while (true) {
				if (routes.size() == 1) {
					next = routes.get(0);
					if (matches.apply(next)) {
						if (addMatch)
							result.add(next);
						return result;
					}
					if (bounds.apply(next))
						return null;
				} else {
					next = null;
					int minDist = UNREACHABLE;
					for (ISynState follower : routes) {
						if (matches.apply(follower)) {
							if (addMatch)
								result.add(follower);
							return result;
						}
						if (!bounds.apply(follower) && follower instanceof ISynEmitterState) {
							ISynEmitterState navFolower = (ISynEmitterState) follower;
							int dist = navFolower.getDistanceTo(matches, bounds, stack);
							if (dist < minDist) {
								next = follower;
								minDist = dist;
							}
						}
					}
				}
				if (next == null || next instanceof ISynAbsorberState)
					return null;
				if (next.getType().isRuleCallExit())
					stack = stack.cloneAndPop();
				else if (next.getType().isRuleCallEnter())
					stack = stack.cloneAndPush((RuleCall) next.getGrammarElement());
				routes = next.getFollowers();
				visited.add(next);
				result.add(next);
			}
		}

		public List<ISynState> getShortestPathToAbsorber(RCStack stack) {
			if (involvesRuleExit())
				return getShortestPathToElement(SynPredicates.absorber(), stack);
			if (shortestPathToAbsorber == null)
				shortestPathToAbsorber = getShortestPathTo(SynPredicates.absorber(), SynPredicates.absorber(), stack,
						false);
			return shortestPathToAbsorber;
		}

		protected List<ISynState> getShortestPathToElement(Predicate<ISynState> matches, RCStack stack) {
			List<ISynState> result = Lists.newArrayList();
			ISynNavigable current = this;
			if (involvesRuleExit()) {
				while (!stack.isEmpty()) {
					List<ISynState> r = current.getShortestPathTo(SynPredicates.ruleCallExit(stack.peek()),
							SynPredicates.ruleCallExitsOrAbsorber(), stack, true);
					if (r != null) {
						result.addAll(r);
						stack = stack.cloneAndPop();
						current = (ISynNavigable) r.get(r.size() - 1);
					} else
						break;
				}
			}
			List<ISynState> r = current.getShortestPathTo(matches, SynPredicates.ruleCallExitsOrAbsorber(), stack,
					false);
			if (r != null)
				result.addAll(r);
			return result;
		}

		public ISynAbsorberState getTarget() {
			return target;
		}

		public boolean hasEmitters() {
			if (getFollowers().size() == 1 && getFollowers().get(0) instanceof ISynAbsorberState)
				return false;
			return true;
		}

		protected boolean involves(ISynState from, Set<SynStateType> types, Set<ISynState> visited) {
			if (types.contains(from.getType()))
				return true;
			if (!visited.add(from))
				return false;
			for (ISynState state : from.getFollowers())
				if (!(state instanceof ISynAbsorberState) && involves(state, types, visited))
					return true;
			return false;
		}

		protected boolean involves(Set<SynStateType> types) {
			Set<ISynState> visited = Sets.newHashSet();
			for (ISynState state : followers)
				if (involves(state, types, visited))
					return true;
			return false;
		}

		protected Boolean involvesRuleExit() {
			if (involvesRuleExit == null)
				involvesRuleExit = involves(EnumSet.of(SynStateType.UNASSIGNED_PARSER_RULE_EXIT));
			return involvesRuleExit;
		}

		public boolean involvesUnassignedTokenRuleCalls() {
			if (involvesUnassignedTokenRuleCalls == null)
				involvesUnassignedTokenRuleCalls = involves(EnumSet.of(SynStateType.UNASSIGNED_DATATYPE_RULE_CALL,
						SynStateType.UNASSIGNED_TERMINAL_RULE_CALL));
			return involvesUnassignedTokenRuleCalls;
		}

		public boolean isSyntacticallyAmbiguous() {
			if (syntacticallyAmbiguous == null)
				syntacticallyAmbiguous = isSyntacticallyAmbiguous(followers);
			return syntacticallyAmbiguous;
		}

		protected boolean isSyntacticallyAmbiguous(ISynState state, RCStack exits, RCStack stack,
				List<RCStack> results, Set<ISynState> visited) {
			if (!visited.add(state))
				return true;
			if (state instanceof ISynAbsorberState) {
				results.add(exits);
				return false;
			}
			switch (state.getType().getSimpleType()) {
				case RULECALL_ENTER:
					stack = stack.cloneAndPush((RuleCall) state.getGrammarElement());
					break;
				case RULECALL_EXIT:
					RuleCall rc = (RuleCall) state.getGrammarElement();
					if (!stack.isEmpty()) {
						if (rc == stack.peek())
							stack = stack.cloneAndPop();
						else
							return false;
					} else {
						if (exits.contains(rc))
							return false;
						else {
							visited = Sets.newHashSet();
							exits = exits.cloneAndPush(rc);
						}
					}
					break;
				default:
					break;
			}
			for (ISynState follower : state.getFollowers())
				if (isSyntacticallyAmbiguous(follower, exits, stack, results, visited))
					return true;
			return false;
		}

		protected boolean isSyntacticallyAmbiguous(List<ISynState> states) {
			RCStack exits = new RCStack();
			RCStack stack = new RCStack();
			List<RCStack> results = Lists.newArrayList();
			Set<ISynState> visited = Sets.newHashSet();
			for (ISynState state : states)
				if (isSyntacticallyAmbiguous(state, exits, stack, results, visited))
					return true;
			return results.size() != Sets.newHashSet(results).size();
		}
	}

	protected abstract static class SynState implements ISynState {

		protected AbstractElement element;

		protected List<ISynState> followers = Collections.emptyList();

		protected SynStateType type;

		public SynState(SynStateType type, AbstractElement element) {
			super();
			this.type = type;
			this.element = element;
			this.followers = Collections.emptyList();
		}

		protected void addFollower(ISynState follower) {
			if (followers.isEmpty())
				followers = Lists.newArrayList();
			followers.add(follower);
		}

		protected void addFollower(List<ISynState> follower) {
			if (followers.isEmpty())
				followers = Lists.newArrayList();
			followers.addAll(follower);
		}

		public List<ISynState> getFollowers() {
			return followers;
		}

		public AbstractElement getGrammarElement() {
			return element;
		}

		public SynStateType getType() {
			return type;
		}

		protected void setFollowers(List<ISynState> followers) {
			this.followers = followers;
		}

		@Override
		public String toString() {
			if (type == null)
				return "(type is null)";
			GrammarElementFullTitleSwitch titles = new GrammarElementFullTitleSwitch();
			switch (type.getSimpleType()) {
				case ELEMENT:
					return element == null ? "(null)" : titles.doSwitch(element);
				case RULECALL_ENTER:
					return ">>" + (element == null ? "(null)" : titles.doSwitch(element));
				case RULECALL_EXIT:
					return "<<" + (element == null ? "(null)" : titles.doSwitch(element));
				case START:
					return "start";
				case STOP:
					return "stop";
			}
			return "";
		}

	}

	protected static class SynTransition extends SynNavigable implements ISynTransition {

		protected ISynAbsorberState source;

		public SynTransition(ISynAbsorberState source, ISynAbsorberState target) {
			super(SynStateType.TRANSITION, null, target);
			this.source = source;
		}

		public ISynAbsorberState getSource() {
			return source;
		}

		@Override
		public String toString() {
			return source + " -> " + target;
		}

	}

	protected Map<Action, ISynAbsorberState> cacheAction = Maps.newHashMap();

	protected Map<ParserRule, ISynAbsorberState> cacheRule = Maps.newHashMap();

	protected SequencerPDAProvider pdaProvider = createSequencerPDAProvider();

	protected boolean canReachAbsorber(IPDAState from, IPDAState to, Set<IPDAState> visited) {
		if (isMandatoryAbsorber(from.getGrammarElement()) || !visited.add(from))
			return false;
		for (IPDAState follower : from.getFollowers())
			if (follower == to)
				return true;
			else if (canReachAbsorber(follower, to, visited))
				return true;
		return false;
	}

	protected void collectFollowingAbsorberStates(IPDAState state, boolean collect, Set<IPDAState> visited,
			Set<IPDAState> absorber) {
		if (collect) {
			if (!visited.add(state))
				return;
			if (isMandatoryAbsorber(state.getGrammarElement()) || state.getType() == PDAStateType.STOP) {
				absorber.add(state);
				return;
			} else if (isOptionalAbsorber(state.getGrammarElement()))
				absorber.add(state);
		}
		for (IPDAState follower : state.getFollowers())
			collectFollowingAbsorberStates(follower, true, visited, absorber);
	}

	protected SynAbsorberState createAbsorberState(IPDAState state, Map<IPDAState, SynAbsorberState> absorbers,
			Map<SynAbsorberState, Map<IPDAState, SynState>> emitters) {
		SynAbsorberState result = absorbers.get(state);
		if (result != null)
			return result;
		if (state.getType() == PDAStateType.STOP) {
			absorbers.put(state, result = createAbsorberState(SynStateType.STOP, null));
			return result;
		}
		absorbers.put(state, result = createAbsorberState(getType(state), state.getGrammarElement()));
		Set<IPDAState> followers = Sets.newHashSet();
		collectFollowingAbsorberStates(state, false, Sets.<IPDAState> newHashSet(), followers);
		for (IPDAState follower : followers) {
			SynAbsorberState target = createAbsorberState(follower, absorbers, emitters);
			SynTransition transition = createTransition(result, target);
			Map<IPDAState, SynState> emitter = emitters.get(target);
			if (emitter == null)
				emitters.put(target, emitter = Maps.newHashMap());
			transition.setFollowers(createEmitterStates(state, follower, target, emitter));
			result.addTransition(transition);
		}
		return result;
	}

	protected SynAbsorberState createAbsorberState(SynStateType type, AbstractElement element) {
		return new SynAbsorberState(type, element);
	}

	protected SynState createEmitterState(SynStateType type, AbstractElement element, SynAbsorberState target) {
		return new SynEmitterState(type, element, target);
	}

	protected List<ISynState> createEmitterStates(IPDAState from, IPDAState to, SynAbsorberState target,
			Map<IPDAState, SynState> emitters) {
		List<ISynState> result = Lists.newArrayList();
		for (IPDAState next : from.getFollowers())
			if (next == to)
				result.add(target);
			else if (canReachAbsorber(next, to, Sets.<IPDAState> newHashSet())) {
				SynState emitter = emitters.get(next);
				if (emitter == null) {
					emitters.put(next, emitter = createEmitterState(getType(next), next.getGrammarElement(), target));
					emitter.setFollowers(createEmitterStates(next, to, target, emitters));
				}
				result.add(emitter);
			}
		return result;
	}

	protected SequencerNFAProvider createSequenceParserNFAProvider() {
		return new SequencerNFAProvider();
	}

	protected SequencerPDAProvider createSequencerPDAProvider() {
		return new SequencerPDAProvider(createSequenceParserNFAProvider());
	}

	protected SynTransition createTransition(SynAbsorberState source, SynAbsorberState target) {
		return new SynTransition(source, target);
	}

	public ISynAbsorberState getPDA(Action context) {
		ISynAbsorberState result = cacheAction.get(context);
		if (result == null) {
			Map<IPDAState, SynAbsorberState> absorbers = Maps.newHashMap();
			Map<SynAbsorberState, Map<IPDAState, SynState>> emitters = Maps.newHashMap();
			result = createAbsorberState(pdaProvider.getPDA(context), absorbers, emitters);
			cacheAction.put(context, result);
		}
		return result;
	}

	public ISynAbsorberState getPDA(ParserRule context) {
		ISynAbsorberState result = cacheRule.get(context);
		if (result == null) {
			Map<IPDAState, SynAbsorberState> absorbers = Maps.newHashMap();
			Map<SynAbsorberState, Map<IPDAState, SynState>> emitters = Maps.newHashMap();
			result = createAbsorberState(pdaProvider.getPDA(context), absorbers, emitters);
			cacheRule.put(context, result);
		}
		return result;
	}

	protected SynStateType getType(IPDAState state) {
		switch (state.getType()) {
			case ELEMENT:
				AbstractElement ele = state.getGrammarElement();
				Assignment ass;
				if (ele instanceof Action) {
					if (((Action) ele).getFeature() == null)
						return SynStateType.UNASSIGEND_ACTION_CALL;
					else
						return SynStateType.ASSIGNED_ACTION_CALL;
				} else if (GrammarUtil.containingCrossReference(ele) != null) {
					if (ele instanceof RuleCall) {
						RuleCall rc = (RuleCall) ele;
						if (rc.getRule() instanceof ParserRule)
							return SynStateType.ASSIGNED_CROSSREF_DATATYPE_RULE_CALL;
						if (rc.getRule() instanceof TerminalRule)
							return SynStateType.ASSIGNED_CROSSREF_TERMINAL_RULE_CALL;
						if (rc.getRule() instanceof EnumRule)
							return SynStateType.ASSIGNED_CROSSREF_ENUM_RULE_CALL;
					} else if (ele instanceof Keyword)
						return SynStateType.ASSIGNED_CROSSREF_KEYWORD;
				} else if ((ass = GrammarUtil.containingAssignment(ele)) != null) {
					if (ele instanceof RuleCall) {
						RuleCall rc = (RuleCall) ele;
						if (rc.getRule() instanceof ParserRule) {
							if (rc.getRule().getType().getClassifier() instanceof EClass)
								return SynStateType.ASSIGNED_PARSER_RULE_CALL;
							return SynStateType.ASSIGNED_DATATYPE_RULE_CALL;
						}
						if (rc.getRule() instanceof TerminalRule)
							return SynStateType.ASSIGNED_TERMINAL_RULE_CALL;
						if (rc.getRule() instanceof EnumRule)
							return SynStateType.ASSIGNED_ENUM_RULE_CALL;

					} else if (ele instanceof Keyword) {
						if (GrammarUtil.isBooleanAssignment(ass))
							return SynStateType.ASSIGNED_BOOLEAN_KEYWORD;
						else
							return SynStateType.ASSIGNED_KEYWORD;
					}
				} else {
					if (ele instanceof RuleCall) {
						RuleCall rc = (RuleCall) ele;
						if (rc.getRule() instanceof ParserRule)
							return SynStateType.UNASSIGNED_DATATYPE_RULE_CALL;
						if (rc.getRule() instanceof TerminalRule)
							return SynStateType.UNASSIGNED_TERMINAL_RULE_CALL;
					} else if (ele instanceof Keyword)
						return SynStateType.UNASSIGEND_KEYWORD;
				}
				break;
			case RULECALL_ENTER:
				return SynStateType.UNASSIGNED_PARSER_RULE_ENTER;
			case RULECALL_EXIT:
				return SynStateType.UNASSIGNED_PARSER_RULE_EXIT;
			case START:
				return SynStateType.START;
			case STOP:
				return SynStateType.STOP;
		}
		throw new RuntimeException("no type found for " + state);
	}

	protected boolean isMandatoryAbsorber(AbstractElement ele) {
		if (ele == null)
			return true;
		if (GrammarUtil.isAssigned(ele))
			return true;
		if (GrammarUtil.isAssignedAction(ele))
			return true;
		//		if (GrammarUtil.isDatatypeRuleCall(ele))
		//			return true;
		return false;
	}

	protected boolean isOptionalAbsorber(AbstractElement ele) {
		return false;
	}

}
