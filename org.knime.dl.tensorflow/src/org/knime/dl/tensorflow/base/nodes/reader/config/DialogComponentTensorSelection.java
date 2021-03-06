/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 */
package org.knime.dl.tensorflow.base.nodes.reader.config;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponent;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.dl.core.DLTensorShape;
import org.knime.dl.core.DLTensorSpec;

/**
 * DialogComponent for selecting tensors from a list of tensors in a "add-remove" fashion.
 *
 * @author Benjamin Wilhelm, KNIME GmbH, Konstanz, Germany
 */
public class DialogComponentTensorSelection extends DialogComponent {

	private Collection<DLTensorSpec> m_tensors;

	private final GridBagConstraints m_gbc;

	private final JButton m_addButton;

	private final Function<DLTensorSpec, String> m_identifierFunc;

	/**
	 * Creates a new {@link DialogComponentTensorSelection}.
	 *
	 * @param stringArrayModel the settings model to save the tensor identifiers
	 * @param title the title of the component
	 * @param tensors a collection of available tensors to choose from
	 * @param identifierFunc a function which give the identifier of a given tensor
	 */
	public DialogComponentTensorSelection(final SettingsModelStringArray stringArrayModel, final String title,
			final Collection<DLTensorSpec> tensors, final Function<DLTensorSpec, String> identifierFunc) {
		super(stringArrayModel);
		m_tensors = tensors;
		m_identifierFunc = identifierFunc;

		// Set layout
		getComponentPanel().setLayout(new GridBagLayout());
		m_gbc = new GridBagConstraints();

		// Set the border
		final TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), title);
		getComponentPanel().setBorder(border);

		// Add "add" button
		m_gbc.insets = new Insets(5, 5, 5, 5);
		m_gbc.anchor = GridBagConstraints.FIRST_LINE_END;
		m_gbc.gridx = 3;
		m_gbc.gridy = 0;
		m_gbc.weightx = 1;
		m_gbc.weighty = 1;
		m_addButton = new JButton("Add");
		getComponentPanel().add(m_addButton, m_gbc);

		// Add listeners
		m_addButton.addActionListener(e -> showAddDialog());
		getModel().addChangeListener(e -> updateComponent());
		updateComponent();
	}

	@Override
	protected void updateComponent() {
		// Get the selected strings
		final SettingsModelStringArray model = getModelStringArray();
		final Collection<String> selected = Arrays.asList(model.getStringArrayValue());

		// Remove all panels
		Arrays.stream(getComponentPanel().getComponents()).filter(c -> c instanceof TensorPanel)
				.forEach(c -> getComponentPanel().remove(c));

		// Add selected panels
		m_gbc.anchor = GridBagConstraints.FIRST_LINE_START;
		m_gbc.fill = GridBagConstraints.HORIZONTAL;
		m_gbc.gridy = 1;
		m_gbc.gridwidth = 3;
		m_gbc.weightx = 1;
		m_tensors.stream().filter(t -> selected.contains(m_identifierFunc.apply(t))).forEach(t -> {
			final TensorPanel panel = new TensorPanel(t.getName(), t.getShape(), t.getElementType());
			getComponentPanel().add(panel, m_gbc);
			m_gbc.gridy++;

			// Action listener for the remove button
			panel.m_removeButton.addActionListener(e -> {
				final List<String> current = new ArrayList<>(Arrays.asList(model.getStringArrayValue()));
				current.remove(m_identifierFunc.apply(t));
				model.setStringArrayValue(current.toArray(new String[current.size()]));
				updateComponent();
			});
		});

		// Propagate the enabled status
		setEnabledComponents(model.isEnabled());

		// Disable the button if there is no unselected tensor
		final boolean unselectedAvailable = !getSelectableTensors().isEmpty();
		m_addButton.setEnabled(unselectedAvailable && model.isEnabled());

		getComponentPanel().revalidate();
		getComponentPanel().repaint();
	}

	@Override
	protected void validateSettingsBeforeSave() throws InvalidSettingsException {
		// Remove strings that aren't available
		final SettingsModelStringArray model = getModelStringArray();
		final List<String> current = Arrays.asList(model.getStringArrayValue());
		final List<String> selected = m_tensors.stream().map(t -> m_identifierFunc.apply(t))
				.filter(t -> current.contains(t)).collect(Collectors.toList());
		model.setStringArrayValue(selected.toArray(new String[selected.size()]));
	}

	@Override
	protected void checkConfigurabilityBeforeLoad(final PortObjectSpec[] specs) throws NotConfigurableException {
		// Always configurable
	}

	@Override
	protected void setEnabledComponents(final boolean enabled) {
		getComponentPanel().setEnabled(enabled);

		// Disable/enable all components
		Arrays.stream(getComponentPanel().getComponents()).forEach(c -> c.setEnabled(enabled));
	}

	@Override
	public void setToolTipText(final String text) {
		getComponentPanel().setToolTipText(text);
	}

	/**
	 * Updates the available tensors.
	 *
	 * @param tensors the available tensors
	 */
	public void setTensorOptions(final Collection<DLTensorSpec> tensors) {
		m_tensors = tensors;
		updateComponent();
	}

	private List<DLTensorSpec> getSelectableTensors() {
		final SettingsModelStringArray model = getModelStringArray();
		final List<String> selected = Arrays.asList(model.getStringArrayValue());
		return m_tensors.stream().filter(t -> !selected.contains(m_identifierFunc.apply(t)))
				.collect(Collectors.toList());
	}

	private void showAddDialog() {
		final List<String> selectableStrings = getSelectableTensors().stream().map(t -> m_identifierFunc.apply(t))
				.collect(Collectors.toList());
		final SettingsModelString smTensor = new SettingsModelString("tensor", selectableStrings.get(0));
		final int selectedOption = JOptionPane.showConfirmDialog(getComponentPanel(),
				new AddTensorDialogPanel(smTensor, selectableStrings), "Add tensor...", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (selectedOption == JOptionPane.OK_OPTION) {
			final SettingsModelStringArray model = getModelStringArray();
			final List<String> current = new ArrayList<>(Arrays.asList(model.getStringArrayValue()));
			current.add(smTensor.getStringValue());
			model.setStringArrayValue(current.toArray(new String[current.size()]));
			updateComponent();
		}
	}

	private SettingsModelStringArray getModelStringArray() {
		return (SettingsModelStringArray) getModel();
	}

	private class TensorPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		private final JButton m_removeButton;

		private TensorPanel(final String name, final DLTensorShape shape, final Class<?> elementType) {
			super(new GridBagLayout());
			final GridBagConstraints gbc = new GridBagConstraints();
			final TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), name);
			setBorder(border);

			gbc.insets = new Insets(5, 5, 5, 5);
			gbc.anchor = GridBagConstraints.FIRST_LINE_START;
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.weightx = 2;
			add(new JLabel("Shape: " + shape.toString()), gbc);

			gbc.anchor = GridBagConstraints.FIRST_LINE_END;
			gbc.gridx++;
			gbc.weightx = 1;
			m_removeButton = new JButton("remove");
			add(m_removeButton, gbc);

			gbc.anchor = GridBagConstraints.FIRST_LINE_START;
			gbc.gridx = 0;
			gbc.gridy++;
			gbc.weightx = 2;
			add(new JLabel("Type: " + elementType.getSimpleName()), gbc);
		}

		@Override
		public void setEnabled(final boolean enabled) {
			super.setEnabled(enabled);
			Arrays.stream(getComponents()).forEach(c -> c.setEnabled(enabled));
		}
	}

	private class AddTensorDialogPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		public AddTensorDialogPanel(final SettingsModelString sm, final List<String> selectableStrings) {
			super(new GridBagLayout());
			final GridBagConstraints gbc = new GridBagConstraints();
			gbc.insets = new Insets(5, 5, 5, 5);
			gbc.gridx = 0;
			gbc.gridy = 0;
			gbc.weightx = 1;
			gbc.anchor = GridBagConstraints.WEST;
			gbc.fill = GridBagConstraints.VERTICAL;

			final DialogComponentStringSelection dcTensor = new DialogComponentStringSelection(sm, "Tensor",
					selectableStrings);
			add(dcTensor.getComponentPanel(), gbc);
		}
	}
}
