/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo.
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app;

import android.text.TextUtils;
import android.util.JsonReader;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by murrayc on 7/30/14.
 */
public class DecisionTree {

    private static final String NODE_ROOT = "murrayc_zoonverse_questions";
    private static final String NODE_QUESTION = "question";
    private static final String NODE_CHECKBOX = "checkbox";
    private static final String NODE_ANSWER = "answer";
    //TODO: Make this private and add accessors.
    public final Hashtable<String, Question> questionsMap = new Hashtable<>();

    /**
     * @param inputStreamTree        The XMl file containing the decision tree.
     * @param inputStreamTranslation A JSON file containing translations of the question and answers,
     *                               such as https://github.com/zooniverse/Galaxy-Zoo/blob/master/public/locales/es.json
     */
    public DecisionTree(final InputStream inputStreamTree, final InputStream inputStreamTranslation) {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        //Disable feature that we don't need and which just slow the parsing down:
        //TODO: Confirm that this actually makes a difference.
        dbf.setNamespaceAware(false);
        dbf.setValidating(false);

        DocumentBuilder documentBuilder;
        try {
            documentBuilder = dbf.newDocumentBuilder();
        } catch (final ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        org.w3c.dom.Document xmlDocument;

        try {
            xmlDocument = documentBuilder.parse(inputStreamTree);
        } catch (final SAXException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        final Element rootNode = xmlDocument.getDocumentElement();
        if (!TextUtils.equals(rootNode.getNodeName(), NODE_ROOT)) {
            android.util.Log.v("android-galaxyzoo", "Unexpected XML root node name found: " + rootNode.getNodeName());
            return;
        }

        final List<Node> listQuestions = getChildrenByTagName(rootNode, NODE_QUESTION);
        for (final Node node : listQuestions) {
            if (!(node instanceof Element)) {
                continue;
            }

            final Element element = (Element) node;
            final Question question = loadQuestion(element);
            questionsMap.put(question.getId(), question);
        }

        //Load the translation if one was provided:
        //We don't avoid loading the English strings before,
        //because the translation might be incomplete.
        //TODO: Find an efficient way to avoid loading English strings that will be replaced,
        //maybe by loading the translation first.
        if (inputStreamTranslation != null) {
            loadTranslation(inputStreamTranslation);
        }
    }

    private void loadTranslation(final InputStream inputStreamTranslation) {
        final JsonReader reader;
        try {
            reader = new JsonReader(new InputStreamReader(inputStreamTranslation, "UTF-8"));
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("questions")) { //We ignore the "zooniverse" and "quiz_questions" objects
                    readJsonQuestions(reader);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (final UnsupportedEncodingException e) {
            Log.info("DecisionTree: UnsupportedEncodingException parsing JSON", e);
        } catch (final IOException e) {
            Log.info("DecisionTree: IOException parsing JSON", e);
        } catch (final IllegalStateException e) {
            Log.info("DecisionTree: IllegalStateException parsing JSON", e);
        }
    }

    private void readJsonQuestions(final JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            final String questionId = reader.nextName();

            final Question question = questionsMap.get(questionId);
            if (question != null) {
                readJsonQuestion(reader, question);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private void readJsonQuestion(final JsonReader reader, final Question question) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            final String name = reader.nextName();
            switch (name) {
                case "text":
                    question.setText(reader.nextString());
                    break;
                case "title":
                    question.setTitle(reader.nextString());
                    break;
                case "help":
                    question.setHelp(reader.nextString());
                    break;
                case "answers": {
                    readJsonAnswers(reader, question.answers);
                    break;
                }
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    private void readJsonAnswers(final JsonReader reader, final List<Answer> answers) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            final String answerId = reader.nextName();

            final Answer answer = getAnswerWithId(answers, answerId);
            if (answer != null) {
                answer.setText(reader.nextString());
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

    }

    private Answer getAnswerWithId(final List<Answer> answers, final String id) {
        for (final Answer answer : answers) {
            if (TextUtils.equals(id, answer.getId())) {
                return answer;
            }
        }

        return null;
    }

    public Question getFirstQuestion() {
        if (questionsMap == null) {
            return null;
        }

        return getQuestion("sloan-0"); //TODO: Awful hack. Use an ordered collection?
    }

    public Question getQuestionOrFirst(final String questionId) {
        if (TextUtils.isEmpty(questionId)) {
            return getFirstQuestion();
        } else {
            return getQuestion(questionId);
        }
    }

    public Question getQuestion(final String questionId) {
        if (questionsMap == null) {
            return null;
        }

        if (questionId == null) {
            Log.error("getQuestion(): questionId was null.");
            return null;
        }

        return questionsMap.get(questionId);
    }

    public Question getNextQuestionForAnswer(final String questionId, final String answerId) {
        final Question question = getQuestion(questionId);
        if (question == null) {
            return null;
        }

        if (question.answers == null) {
            return null;
        }

        //TODO: Use a map for performance if there are many answers:
        //For now we use a list, instead of a map, for the answers, to have an order.
        Answer answer = null;
        for (final Answer anAnswer : question.answers) {
            if (TextUtils.equals(anAnswer.getId(), answerId)) {
                answer = anAnswer;
                break;
            }
        }

        if (answer == null) {
            return null;
        }

        return getQuestion(answer.leadsToQuestionId);
    }

    /**
     * getElementsByTagName() is recursive, but we do not want that.
     *
     * @param parentNode
     * @param tagName
     * @return
     */
    private List<Node> getChildrenByTagName(final Element parentNode, final String tagName) {
        final List<Node> result = new ArrayList<>();

        final NodeList list = parentNode.getElementsByTagName(tagName);
        final int num = list.getLength();
        for (int i = 0; i < num; i++) {
            final Node node = list.item(i);
            if (node == null) {
                continue;
            }

            final Node itemParentNode = node.getParentNode();
            if (itemParentNode.equals(parentNode)) {
                result.add(node);
            }
        }

        return result;
    }

    /**
     * getElementsByTagName() is recursive, but we do not want that.
     *
     * @param parentNode
     * @param tagName
     * @return
     */
    private Node getFirstChildByTagName(final Element parentNode, final String tagName) {
        final NodeList list = parentNode.getElementsByTagName(tagName);
        final int num = list.getLength();
        for (int i = 0; i < num; i++) {
            final Node node = list.item(i);
            if (node == null) {
                continue;
            }

            final Node itemParentNode = node.getParentNode();
            if (itemParentNode.equals(parentNode)) {
                return node;
            }
        }

        return null;
    }

    private String getTextOfChildNode(final Element element, final String tagName) {
        final Node node = getFirstChildByTagName(element, tagName);
        if (node == null)
            return null;

        return node.getTextContent();
    }

    private Question loadQuestion(final Element questionNode) {
        final Question result = new Question(
                questionNode.getAttribute("id"),
                getTextOfChildNode(questionNode, "title"),
                getTextOfChildNode(questionNode, "text"),
                getTextOfChildNode(questionNode, "help"));

        final List<Node> listCheckboxes = getChildrenByTagName(questionNode, NODE_CHECKBOX);
        for (final Node node : listCheckboxes) {
            if (!(node instanceof Element)) {
                continue;
            }

            final Element element = (Element) node;
            final Checkbox checkbox = loadCheckbox(element);
            result.checkboxes.add(checkbox);
        }

        final List<Node> listAnswers = getChildrenByTagName(questionNode, NODE_ANSWER);
        for (final Node node : listAnswers) {
            if (!(node instanceof Element)) {
                continue;
            }

            final Element element = (Element) node;
            final Answer answer = loadAnswer(element);
            result.answers.add(answer);
        }

        return result;
    }

    private Checkbox loadCheckbox(final Element checkboxNode) {
        return new Checkbox(
                checkboxNode.getAttribute("id"),
                getTextOfChildNode(checkboxNode, "text"),
                checkboxNode.getAttribute("icon"),
                Integer.parseInt(checkboxNode.getAttribute("examplesCount")));
    }

    private Answer loadAnswer(final Element answerNode) {
        return new Answer(
                answerNode.getAttribute("id"),
                getTextOfChildNode(answerNode, "text"),
                answerNode.getAttribute("icon"),
                answerNode.getAttribute("leadsTo"),
                Integer.parseInt(answerNode.getAttribute("examplesCount")));
    }

    static class BaseButton {
        private final String id;
        private String text;
        private final String icon;
        private final int examplesCount;

        BaseButton(final String id, final String text, final String icon, int examplesCount) {
            this.id = id;
            this.text = text;
            this.icon = icon;
            this.examplesCount = examplesCount;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }

        public String getIcon() {
            return icon;
        }

        int getExamplesCount() {
            return examplesCount;
        }

        public String getExampleIconName(String questionId, int exampleIndex) {
            return questionId + "_" + getId() + "_" + exampleIndex;
        }
    }

    //These are multiple-selection.
    static class Checkbox extends BaseButton {
        Checkbox(final String id, final String text, final String icon, int examplesCount) {
            super(id, text, icon, examplesCount);
        }
    }

    //These are single selection.
    //Sometimes it's just "Done" to accept the checkbox selections.
    static class Answer extends BaseButton {
        private final String leadsToQuestionId;

        Answer(final String id, final String text, final String icon, final String leadsToQuestionId, int examplesCount) {
            super(id, text, icon, examplesCount);
            this.leadsToQuestionId = leadsToQuestionId;
        }
    }

    public static class Question {
        public final List<Checkbox> checkboxes = new ArrayList<>();
        public final List<Answer> answers = new ArrayList<>();
        private final String id;
        private String title;
        private String text;
        private String help;

        Question(final String id, final String title, final String text, final String help) {
            this.id = id;
            this.title = title;
            this.text = text;
            this.help = help;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(final String title) {
            this.title = title;
        }

        public String getText() {
            return text;
        }

        public void setText(final String text) {
            this.text = text;
        }

        public String getHelp() {
            return help;
        }

        public void setHelp(final String help) {
            this.help = help;
        }

        public boolean hasCheckboxes() {
            if (checkboxes == null) {
                return false;
            }

            if (checkboxes.size() == 0) {
                return false;
            }

            return true;
        }
    }
}